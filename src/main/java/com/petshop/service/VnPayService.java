package com.petshop.service;

import com.petshop.config.VnPayProperties;
import com.petshop.entity.Order;
import com.petshop.entity.OrderStatusHistory;
import com.petshop.entity.PaymentTransaction;
import com.petshop.entity.enums.*;
import com.petshop.repository.OrderRepository;
import com.petshop.repository.OrderStatusHistoryRepository;
import com.petshop.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** VNPay 2.1 Sandbox integration. Only a verified server IPN mutates payment state. */
@Service
@RequiredArgsConstructor
public class VnPayService {
    private static final String GATEWAY = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter GATEWAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnPayProperties properties;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderService orderService;

    public boolean isConfigured() {
        if (properties.getTmnCode() == null || properties.getTmnCode().isBlank()
                || properties.getHashSecret() == null || properties.getHashSecret().isBlank()
                || properties.getReturnUrl() == null || properties.getReturnUrl().isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(properties.getReturnUrl());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional
    public String createPaymentUrl(Order submittedOrder, String clientIp) {
        if (!isConfigured()) {
            throw new IllegalStateException("VNPay Sandbox chưa được cấu hình. Vui lòng chọn COD.");
        }
        Order order = orderRepository.findByIdForUpdate(submittedOrder.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (order.getPaymentMethod() != PaymentMethod.VNPAY || order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new IllegalStateException("Đơn hàng không còn chờ thanh toán VNPay.");
        }
        PaymentTransaction transaction = transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .filter(t -> t.getPaymentMethod() == PaymentMethod.VNPAY
                        && t.getTransactionStatus() == TransactionStatus.PENDING)
                .findFirst().orElseThrow(() -> new IllegalStateException("Không có giao dịch VNPay đang chờ."));
        if (transaction.getExpiresAt() == null || !transaction.getExpiresAt().isAfter(LocalDateTime.now())
                || transaction.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new IllegalStateException("Giao dịch VNPay hết hạn hoặc số tiền không hợp lệ.");
        }
        transaction.setProviderOrderReference(transaction.getTransactionCode());
        transactionRepository.save(transaction);
        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_Amount", gatewayAmount(transaction.getAmount()));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", transaction.getTransactionCode());
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderCode());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.getReturnUrl());
        // Use the socket address supplied by the server, never an untrusted forwarding header.
        fields.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        fields.put("vnp_CreateDate", GATEWAY_TIME.format(LocalDateTime.now().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(VIETNAM)));
        fields.put("vnp_ExpireDate", GATEWAY_TIME.format(transaction.getExpiresAt().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(VIETNAM)));
        String canonical = canonical(fields);
        return GATEWAY + "?" + canonical + "&vnp_SecureHash=" + sign(canonical);
    }

    @Transactional
    public Map<String, String> processIpn(Map<String, String[]> requestParameters) {
        Optional<Map<String, String>> verified = verifiedFields(requestParameters);
        if (verified.isEmpty()) return response("97", "Invalid Checksum");
        Map<String, String> fields = verified.get();
        if (!validCallbackFields(fields)) return response("99", "Invalid request");
        Optional<Long> orderId = transactionRepository.findOrderIdByTransactionCode(fields.get("vnp_TxnRef"));
        if (orderId.isEmpty()) return response("01", "Order not found");

        // Every cancellation, staff change and IPN locks the same order before reading transactions.
        Order order = orderRepository.findByIdForUpdate(orderId.get()).orElse(null);
        if (order == null) return response("01", "Order not found");
        PaymentTransaction transaction = transactionRepository.findByTransactionCode(fields.get("vnp_TxnRef")).orElse(null);
        if (!matchesTransaction(transaction, order, fields)) return response("04", "Invalid Amount");

        if (transaction.getTransactionStatus() != TransactionStatus.PENDING
                || order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            // A replay/late success never reopens an order or reserves its stock again.
            recordLateSuccess(transaction, fields);
            return response("02", "Order already confirmed");
        }

        if (transaction.getExpiresAt() == null || !transaction.getExpiresAt().isAfter(LocalDateTime.now())) {
            orderService.cancelPendingPaymentOrder(order.getId(), CancelType.PAYMENT_EXPIRED,
                    "Hết thời gian thanh toán VNPay");
            recordCallback(transaction, fields);
            recordLateSuccess(transaction, fields);
            transactionRepository.save(transaction);
            return response("02", "Order already confirmed");
        }

        boolean success = "00".equals(fields.get("vnp_ResponseCode"))
                && "00".equals(fields.get("vnp_TransactionStatus"));
        String providerId = fields.get("vnp_TransactionNo");
        if (success && (providerId == null || !providerId.matches("[0-9]{1,100}") || providerId.matches("0+"))) {
            return response("99", "Invalid transaction");
        }
        if (success && transactionRepository.existsByProviderTransactionIdAndIdNot(providerId, transaction.getId())) {
            return response("99", "Invalid transaction");
        }

        if (success) {
            transaction.setTransactionStatus(TransactionStatus.SUCCESS);
            transaction.setProviderTransactionId(providerId);
            transaction.setPaidAt(LocalDateTime.now());
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setOrderStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            historyRepository.save(OrderStatusHistory.builder().order(order)
                    .oldStatus(OrderStatus.PENDING_PAYMENT).newStatus(OrderStatus.CONFIRMED)
                    .reason("VNPay IPN xác nhận thanh toán thành công").build());
        } else {
            boolean cancelledAtGateway = "24".equals(fields.get("vnp_ResponseCode"));
            orderService.cancelPendingPaymentOrder(order.getId(), cancelledAtGateway
                    ? CancelType.PAYMENT_CANCELLED : CancelType.PAYMENT_FAILED,
                    cancelledAtGateway ? "Khách hàng hủy thanh toán tại VNPay" : "Thanh toán VNPay không thành công");
            transaction.setTransactionStatus(cancelledAtGateway ? TransactionStatus.CANCELLED : TransactionStatus.FAILED);
            transaction.setFailureReason(cancelledAtGateway ? "Khách hàng hủy thanh toán tại VNPay"
                    : "VNPay từ chối thanh toán (" + fields.get("vnp_ResponseCode") + ")");
            // Failure callbacks often contain transaction number 0; it must not occupy the DB's unique index.
        }
        recordCallback(transaction, fields);
        transactionRepository.save(transaction);
        return response("00", "Confirm Success");
    }

    /** Browser Return is informational and read-only, even when its signature is valid. */
    @Transactional(readOnly = true)
    public Optional<Long> findOwnedOrderIdForReturn(Map<String, String[]> requestParameters, Long customerId) {
        if (customerId == null) return Optional.empty();
        Optional<Map<String, String>> verified = verifiedFields(requestParameters);
        if (verified.isEmpty() || !validCallbackFields(verified.get())) return Optional.empty();
        Map<String, String> fields = verified.get();
        PaymentTransaction transaction = transactionRepository.findByTransactionCode(fields.get("vnp_TxnRef")).orElse(null);
        if (transaction == null) return Optional.empty();
        Order order = transaction.getOrder();
        if (!order.getCustomer().getId().equals(customerId) || !matchesTransaction(transaction, order, fields)) {
            return Optional.empty();
        }
        return Optional.of(order.getId());
    }

    private Optional<Map<String, String>> verifiedFields(Map<String, String[]> requestParameters) {
        if (!isConfigured() || requestParameters == null) return Optional.empty();
        Map<String, String> fields = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : requestParameters.entrySet()) {
            if (!entry.getKey().startsWith("vnp_")) continue;
            // Servlet getParameter() would silently choose one value from duplicate keys.
            if (entry.getValue() == null || entry.getValue().length != 1 || entry.getValue()[0] == null) {
                return Optional.empty();
            }
            fields.put(entry.getKey(), entry.getValue()[0]);
        }
        String receivedHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        if (receivedHash == null || !receivedHash.matches("[0-9a-fA-F]{128}")) return Optional.empty();
        if (!MessageDigest.isEqual(sign(canonical(fields)).getBytes(StandardCharsets.US_ASCII),
                receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        return Optional.of(fields);
    }

    private boolean validCallbackFields(Map<String, String> fields) {
        return properties.getTmnCode().equals(fields.get("vnp_TmnCode"))
                && matches(fields.get("vnp_TxnRef"), "[A-Za-z0-9_-]{1,80}")
                && matches(fields.get("vnp_Amount"), "[0-9]{1,20}")
                && matches(fields.get("vnp_ResponseCode"), "[0-9]{2}")
                && matches(fields.get("vnp_TransactionStatus"), "[0-9]{2}")
                && optionalLength(fields.get("vnp_BankCode"), 30)
                && optionalLength(fields.get("vnp_CardType"), 30)
                && optionalLength(fields.get("vnp_TransactionNo"), 100);
    }

    private boolean matchesTransaction(PaymentTransaction transaction, Order order, Map<String, String> fields) {
        if (transaction == null || transaction.getPaymentMethod() != PaymentMethod.VNPAY
                || transaction.getPaymentEnvironment() != PaymentEnvironment.SANDBOX
                || order.getPaymentMethod() != PaymentMethod.VNPAY
                || !transaction.getOrder().getId().equals(order.getId())
                || !transaction.getCustomer().getId().equals(order.getCustomer().getId())
                || !fields.get("vnp_TxnRef").equals(transaction.getProviderOrderReference())
                || transaction.getAmount().compareTo(order.getTotalAmount()) != 0) return false;
        try {
            return new BigDecimal(fields.get("vnp_Amount")).compareTo(new BigDecimal(gatewayAmount(transaction.getAmount()))) == 0;
        } catch (ArithmeticException | IllegalArgumentException e) {
            return false;
        }
    }

    private void recordCallback(PaymentTransaction transaction, Map<String, String> fields) {
        transaction.setSignatureVerified(true);
        transaction.setResponseCode(fields.get("vnp_ResponseCode"));
        transaction.setBankCode(fields.get("vnp_BankCode"));
        transaction.setCardType(fields.get("vnp_CardType"));
        transaction.setCallbackReceivedAt(LocalDateTime.now());
    }

    private void recordLateSuccess(PaymentTransaction transaction, Map<String, String> fields) {
        String reconciliationReason = "IPN VNPay thành công đến sau khi đơn đã đóng; cần đối soát với VNPay.";
        if (transaction.getTransactionStatus() != TransactionStatus.SUCCESS
                && "00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"))
                && !reconciliationReason.equals(transaction.getFailureReason())) {
            // Preserve the closed transaction and unpaid/cancelled order. No automatic refund is implemented.
            recordCallback(transaction, fields);
            transaction.setFailureReason(reconciliationReason);
            transactionRepository.save(transaction);
        }
    }

    private String gatewayAmount(BigDecimal amount) {
        // VNPay VND requires whole dong; never truncate or round the signed amount.
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Số tiền không hợp lệ.");
        try {
            amount.toBigIntegerExact();
            return amount.multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("VNPay chỉ hỗ trợ số tiền nguyên đồng. Vui lòng chọn COD cho đơn này.", e);
        }
    }

    private String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không thể tạo chữ ký thanh toán.", e);
        }
    }

    private boolean matches(String value, String pattern) { return value != null && value.matches(pattern); }
    private boolean optionalLength(String value, int max) { return value == null || value.length() <= max; }
    private Map<String, String> response(String code, String message) { return Map.of("RspCode", code, "Message", message); }
}
