package com.petshop.service.impl;

import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.OrderService;
import com.petshop.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PlatformTransactionManager transactionManager;
    private final com.petshop.config.ShippingFeeConfig shippingFeeConfig;

    @Override
    @Transactional
    public Order checkoutOrder(Long customerId, CheckoutSubmitDto request) {
        validateCheckout(request);
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Khách hàng không tồn tại."));
        if (!"ACTIVE".equals(customer.getStatus()) || !"CUSTOMER".equals(customer.getRole().getCode())) {
            throw new IllegalArgumentException("Tài khoản không được phép đặt hàng.");
        }

        // All cart mutations use this same lock. A second checkout sees the cart after
        // the first one has removed its purchased lines, rather than buying them twice.
        Cart cart = cartRepository.findByUserIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Giỏ hàng rỗng."));
        List<CartItem> selectedItems = cartItemRepository.findByCartId(cart.getId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsSelected()))
                .sorted(Comparator.comparing(item -> item.getVariant().getId()))
                .toList();
        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 sản phẩm để thanh toán.");
        }

        String requestedVoucherCode = trimToNull(request.getVoucherCode());
        Long requestedVoucherId = null;
        long customerVoucherUsage = 0;
        if (requestedVoucherCode != null) {
            requestedVoucherCode = requestedVoucherCode.toUpperCase(Locale.ROOT);
            requestedVoucherId = voucherRepository.findIdByCode(requestedVoucherCode)
                    .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại."));
            // Read order usage before taking SKU/voucher locks. Cancellation takes an
            // order lock before restoring those resources; reading orders afterwards
            // could invert that lock order on SQL Server without snapshot isolation.
            // The cart lock serializes this customer's checkouts. Cancellations can
            // only lower this count, so a concurrent cancellation is conservative.
            customerVoucherUsage = orderRepository.countVoucherUsageByCustomer(customerId, requestedVoucherId);
        }

        // Lock SKUs in a consistent order, and snapshot their current prices while
        // holding the locks. Nothing from the browser determines payable amounts.
        BigDecimal subtotal = BigDecimal.ZERO;
        List<ProductVariant> variants = new ArrayList<>();
        for (CartItem item : selectedItems) {
            Integer quantity = item.getQuantity();
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Số lượng sản phẩm phải là số nguyên dương.");
            }
            ProductVariant variant = variantRepository.findByIdForUpdate(item.getVariant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm trong giỏ hàng không còn tồn tại."));
            if (variant.getStatus() != ProductVariantStatus.ACTIVE
                    || variant.getProduct().getStatus() != ProductStatus.ACTIVE) {
                throw new IllegalStateException("Sản phẩm '" + variant.getProduct().getName()
                        + " - " + variant.getVariantName() + "' hiện không còn kinh doanh.");
            }
            if (variant.getStockQuantity() == null || variant.getStockQuantity() < quantity) {
                throw new IllegalStateException("Sản phẩm '" + variant.getProduct().getName()
                        + " - " + variant.getVariantName() + "' không đủ số lượng trong kho.");
            }
            validateAmount(variant.getPrice(), "Giá sản phẩm không hợp lệ.");
            subtotal = subtotal.add(variant.getPrice().multiply(BigDecimal.valueOf(quantity)));
            validateAmount(subtotal, "Giá trị đơn hàng vượt giới hạn cho phép.");
            variants.add(variant);
        }

        Voucher voucher = null;
        BigDecimal discount = BigDecimal.ZERO;
        String voucherCode = null;
        if (requestedVoucherCode != null) {
            // Keep current metadata, the discount snapshot, and quota reservation
            // under one lock, without rereading existing orders while holding it.
            voucher = voucherRepository.findByCodeForUpdate(requestedVoucherCode)
                    .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại."));
            if (!voucher.getId().equals(requestedVoucherId)) {
                throw new IllegalStateException("Mã giảm giá đã thay đổi. Vui lòng kiểm tra lại.");
            }
            VoucherApplyResultDto result = voucherService.validateAndCalculateDiscount(requestedVoucherCode, null, subtotal);
            if (!result.isValid()) {
                throw new IllegalArgumentException("Mã voucher không hợp lệ: " + result.getMessage());
            }
            int perCustomerLimit = voucher.getPerCustomerLimit() != null && voucher.getPerCustomerLimit() > 0
                    ? voucher.getPerCustomerLimit() : 1;
            if (customerVoucherUsage >= perCustomerLimit) {
                throw new IllegalArgumentException("Bạn đã sử dụng hết số lần cho phép với mã này.");
            }
            discount = result.getDiscountAmount();
            if (discount == null || discount.signum() < 0 || discount.compareTo(subtotal) > 0) {
                throw new IllegalStateException("Giá trị giảm giá không hợp lệ.");
            }
            voucherCode = voucher.getCode();
            voucherService.reserveVoucher(voucher.getId());
        }
        BigDecimal shippingFee = shippingFeeConfig.getShippingFee();
        BigDecimal total = subtotal.subtract(discount).add(shippingFee);
        validateAmount(total, "Giá trị thanh toán vượt giới hạn cho phép.");
        OrderStatus initialStatus = request.getPaymentMethod() == PaymentMethod.VNPAY
                ? OrderStatus.PENDING_PAYMENT : OrderStatus.PENDING_CONFIRMATION;
        Order order = Order.builder()
                .orderCode("ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT))
                .customer(customer).voucher(voucher).voucherCodeSnapshot(voucherCode)
                .recipientName(request.getRecipientName().trim())
                .recipientPhone(request.getRecipientPhone().trim())
                .shippingProvince(request.getShippingProvince().trim())
                .shippingDistrict(request.getShippingDistrict().trim())
                .shippingWard(request.getShippingWard().trim())
                .shippingAddressLine(request.getShippingAddressLine().trim())
                .customerNote(trimToNull(request.getCustomerNote()))
                .subtotalAmount(subtotal).discountAmount(discount).shippingFee(shippingFee).totalAmount(total)
                .orderStatus(initialStatus).paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.UNPAID).build();
        Order savedOrder = orderRepository.save(order);
        if (voucher != null) voucherService.markCustomerVoucherUsed(customerId, voucher.getId());

        List<Long> purchasedVariantIds = new ArrayList<>();
        for (int i = 0; i < selectedItems.size(); i++) {
            CartItem cartItem = selectedItems.get(i);
            ProductVariant variant = variants.get(i);
            if (variantRepository.deductStockAtomic(variant.getId(), cartItem.getQuantity()) != 1) {
                throw new IllegalStateException("Sản phẩm không đủ số lượng trong kho. Vui lòng kiểm tra lại giỏ hàng.");
            }
            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder).variant(variant)
                    .productNameSnapshot(variant.getProduct().getName())
                    .skuCodeSnapshot(variant.getSkuCode()).variantNameSnapshot(variant.getVariantName())
                    .unitPrice(variant.getPrice()).quantity(cartItem.getQuantity()).build();
            orderItemRepository.save(orderItem);
            savedOrder.getItems().add(orderItem);
            purchasedVariantIds.add(variant.getId());
        }
        saveHistory(savedOrder, null, initialStatus,
                "Khách hàng tạo đơn hàng mới (" + request.getPaymentMethod() + ")", customer);

        if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
            String transactionCode = "TXN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .transactionCode(transactionCode).providerOrderReference(transactionCode)
                    .order(savedOrder).customer(customer).paymentMethod(PaymentMethod.VNPAY)
                    .paymentEnvironment(PaymentEnvironment.SANDBOX).amount(total)
                    .transactionStatus(TransactionStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusMinutes(15)).build());
        }
        cartItemRepository.deletePurchasedItems(cart.getId(), purchasedVariantIds);
        log.info("Order {} created for customer {}", savedOrder.getOrderCode(), customerId);
        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getCustomerOrders(Long customerId, OrderStatus status, Pageable pageable) {
        return getCustomerOrders(customerId, status, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getCustomerOrders(Long customerId, OrderStatus status,
                                         LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        if ((fromDate != null && (fromDate.getYear() < 1 || fromDate.getYear() > 9999))
                || (toDate != null && (toDate.getYear() < 1 || toDate.getYear() > 9999))) {
            throw new IllegalArgumentException("Ngày lọc phải nằm trong khoảng năm 1 đến 9999.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        }
        Specification<Order> filters = (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        if (status != null) {
            filters = filters.and((root, query, cb) -> cb.equal(root.get("orderStatus"), status));
        }
        if (fromDate != null) {
            filters = filters.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
        }
        if (toDate != null && !toDate.equals(LocalDate.of(9999, 12, 31))) {
            LocalDateTime exclusiveEnd = toDate.plusDays(1).atStartOfDay();
            filters = filters.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), exclusiveEnd));
        }
        Pageable sorted = pageable.isUnpaged() || pageable.getSort().isSorted() ? pageable : PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Order> page = orderRepository.findAll(filters, sorted);
        if (!page.isEmpty()) {
            orderRepository.findWithItemsByIdIn(page.getContent().stream().map(Order::getId).toList());
        }
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderDetailForCustomer(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        requireOwner(order, customerId);
        initializeDisplayDetails(order);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderByCodeForCustomer(String orderCode, Long customerId) {
        if (orderCode == null || orderCode.isBlank()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng.");
        }
        Order order = orderRepository.findByOrderCodeWithDetails(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        requireOwner(order, customerId);
        initializeDisplayDetails(order);
        return order;
    }

    @Override
    @Transactional
    public void customerCancelOrder(Long orderId, Long customerId, String reason) {
        Order order = lockOrder(orderId);
        requireOwner(order, customerId);
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        boolean unpaidCod = order.getPaymentMethod() == PaymentMethod.COD
                && order.getOrderStatus() == OrderStatus.PENDING_CONFIRMATION
                && order.getPaymentStatus() == PaymentStatus.UNPAID;
        boolean pendingVnpay = order.getPaymentMethod() == PaymentMethod.VNPAY
                && order.getOrderStatus() == OrderStatus.PENDING_PAYMENT
                && isUnpaid(order)
                && transactions.stream().anyMatch(tx -> tx.getPaymentMethod() == PaymentMethod.VNPAY
                        && tx.getTransactionStatus() == TransactionStatus.PENDING);
        if ((!unpaidCod && !pendingVnpay) || hasSuccessfulPayment(transactions)) {
            throw new IllegalStateException("Đơn hàng đã thanh toán, đã được xử lý hoặc không còn chờ thanh toán, không thể tự hủy.");
        }
        performOrderCancellation(order, CancelType.CUSTOMER_CANCEL,
                cancellationReason(reason, "Khách hàng yêu cầu hủy đơn"), order.getCustomer(), transactions);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getOrdersForStaff(OrderStatus status, Pageable pageable) {
        return status != null ? orderRepository.findByOrderStatusOrderByCreatedAtDesc(status, pageable)
                : orderRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderDetailForStaff(Long orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        initializeDisplayDetails(order);
        return order;
    }

    @Override
    @Transactional
    public void confirmOrder(Long orderId, Long staffId) {
        Order order = lockOrder(orderId);
        requireStatus(order, OrderStatus.PENDING_CONFIRMATION);
        if (order.getPaymentMethod() != PaymentMethod.COD || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new IllegalStateException("Chỉ xác nhận đơn COD chưa thanh toán đang chờ xác nhận.");
        }
        transitionStatus(order, OrderStatus.CONFIRMED, "Nhân viên xác nhận đơn hàng", requireUser(staffId));
    }

    @Override
    @Transactional
    public void markPreparing(Long orderId, Long staffId) {
        Order order = lockOrder(orderId);
        requireStatus(order, OrderStatus.CONFIRMED);
        requirePaymentForFulfilment(order);
        transitionStatus(order, OrderStatus.PREPARING, "Đang đóng gói hàng tại kho", requireUser(staffId));
    }

    @Override
    @Transactional
    public void markShipping(Long orderId, Long staffId) {
        Order order = lockOrder(orderId);
        requireStatus(order, OrderStatus.PREPARING);
        requirePaymentForFulfilment(order);
        transitionStatus(order, OrderStatus.SHIPPING, "Đã giao cho đơn vị vận chuyển", requireUser(staffId));
    }

    @Override
    @Transactional
    public void markCompleted(Long orderId, Long staffId) {
        Order order = lockOrder(orderId);
        requireStatus(order, OrderStatus.SHIPPING);
        requirePaymentForFulfilment(order);
        User staff = requireUser(staffId);
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }
        order.setCompletedAt(LocalDateTime.now());
        transitionStatus(order, OrderStatus.COMPLETED, "Giao hàng thành công", staff);
    }

    @Override
    @Transactional
    public void staffRejectOrder(Long orderId, Long staffId, String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("Nhân viên từ chối đơn bắt buộc phải nhập lý do.");
        }
        String reason = cancellationReason(rejectReason, null);
        Order order = lockOrder(orderId);
        requireStatus(order, OrderStatus.PENDING_CONFIRMATION);
        if (order.getPaymentMethod() != PaymentMethod.COD || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new IllegalStateException("Chỉ từ chối đơn COD chưa thanh toán đang chờ xác nhận.");
        }
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (hasSuccessfulPayment(transactions)) {
            throw new IllegalStateException("Không thể từ chối đơn đã thanh toán.");
        }
        performOrderCancellation(order, CancelType.STAFF_REJECT, reason, requireUser(staffId), transactions);
    }

    @Override
    @Transactional
    public void cancelPendingPaymentOrder(Long orderId, CancelType cancelType, String reason) {
        if (cancelType != CancelType.PAYMENT_FAILED && cancelType != CancelType.PAYMENT_CANCELLED
                && cancelType != CancelType.PAYMENT_EXPIRED) {
            throw new IllegalArgumentException("Loại hủy thanh toán không hợp lệ.");
        }
        Order order = lockOrder(orderId);
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentMethod() != PaymentMethod.VNPAY || !isUnpaid(order)
                || hasSuccessfulPayment(transactions)) {
            throw new IllegalStateException("Đơn hàng không còn chờ thanh toán hoặc đã thanh toán thành công.");
        }
        performOrderCancellation(order, cancelType, cancellationReason(reason, cancelType.name()), null, transactions);
    }

    @Override
    public void cancelExpiredPendingPaymentOrders() {
        LocalDateTime now = LocalDateTime.now();
        // Read identifiers without holding any order/SKU locks. Each order then
        // commits independently, so a batch never holds order A's SKU locks while
        // waiting for order B (which a customer or callback may already own).
        List<Long> orderIds = paymentTransactionRepository.findExpiredPendingOrderIds(now);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (Long orderId : orderIds) {
            transaction.executeWithoutResult(status -> cancelExpiredPendingPaymentOrder(orderId, now));
        }
    }

    private void cancelExpiredPendingPaymentOrder(Long orderId, LocalDateTime now) {
        Order order = lockOrder(orderId);
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentMethod() != PaymentMethod.VNPAY || !isUnpaid(order)
                || hasSuccessfulPayment(transactions)) {
            return;
        }
        List<PaymentTransaction> pending = transactions.stream()
                .filter(tx -> tx.getTransactionStatus() == TransactionStatus.PENDING).toList();
        if (!pending.isEmpty() && pending.stream().allMatch(tx -> tx.getExpiresAt() != null
                && !tx.getExpiresAt().isAfter(now))) {
            performOrderCancellation(order, CancelType.PAYMENT_EXPIRED,
                    "Hết thời gian 15 phút thanh toán trực tuyến", null, transactions);
        }
    }

    private Order lockOrder(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng.");
        }
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Người dùng không hợp lệ.");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại."));
        if (!"ACTIVE".equals(actor.getStatus()) || !java.util.Set.of("ADMIN","STAFF").contains(actor.getRole().getCode())) throw new IllegalArgumentException("Tài khoản không có quyền xử lý đơn hàng.");
        return actor;
    }

    private void requireOwner(Order order, Long customerId) {
        if (customerId == null || !order.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng.");
        }
    }

    private boolean isUnpaid(Order order) {
        return order.getPaymentStatus() == PaymentStatus.UNPAID || order.getPaymentStatus() == PaymentStatus.FAILED;
    }

    private boolean hasSuccessfulPayment(List<PaymentTransaction> transactions) {
        return transactions.stream().anyMatch(tx -> tx.getTransactionStatus() == TransactionStatus.SUCCESS);
    }

    private void requireStatus(Order order, OrderStatus required) {
        if (order.getOrderStatus() != required) {
            throw new IllegalStateException("Đơn hàng phải ở trạng thái " + required
                    + ". Trạng thái hiện tại: " + order.getOrderStatus());
        }
    }

    private void requirePaymentForFulfilment(Order order) {
        if ((order.getPaymentMethod() == PaymentMethod.COD && order.getPaymentStatus() != PaymentStatus.UNPAID)
                || (order.getPaymentMethod() == PaymentMethod.VNPAY && order.getPaymentStatus() != PaymentStatus.PAID)) {
            throw new IllegalStateException("Trạng thái thanh toán không phù hợp để xử lý đơn hàng.");
        }
    }

    private void initializeDisplayDetails(Order order) {
        order.getItems().size();
        order.getCustomer().getFullName();
        if (order.getCancelledBy() != null) {
            order.getCancelledBy().getFullName();
        }
        order.getStatusHistories().forEach(history -> {
            if (history.getChangedBy() != null) {
                history.getChangedBy().getFullName();
            }
        });
    }

    private void transitionStatus(Order order, OrderStatus newStatus, String reason, User changedBy) {
        OrderStatus previous = order.getOrderStatus();
        order.setOrderStatus(newStatus);
        orderRepository.save(order);
        saveHistory(order, previous, newStatus, reason, changedBy);
    }

    private void saveHistory(Order order, OrderStatus oldStatus, OrderStatus newStatus, String reason, User actor) {
        OrderStatusHistory history = OrderStatusHistory.builder().order(order).oldStatus(oldStatus)
                .newStatus(newStatus).reason(reason).changedBy(actor).build();
        statusHistoryRepository.save(history);
    }

    private void performOrderCancellation(Order order, CancelType cancelType, String reason,
                                          User actor, List<PaymentTransaction> transactions) {
        if (order.getOrderStatus() != OrderStatus.PENDING_CONFIRMATION
                && order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Đơn hàng đã được xử lý, không thể hủy lại.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID || hasSuccessfulPayment(transactions)) {
            throw new IllegalStateException("Không thể hủy đơn đã thanh toán.");
        }
        OrderStatus previous = order.getOrderStatus();
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelType(cancelType);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(actor);
        order.setPaymentStatus(cancelType == CancelType.PAYMENT_FAILED ? PaymentStatus.FAILED : PaymentStatus.CANCELLED);
        orderRepository.save(order);

        for (PaymentTransaction transaction : transactions) {
            if (transaction.getTransactionStatus() == TransactionStatus.PENDING) {
                transaction.setTransactionStatus(cancelType == CancelType.PAYMENT_EXPIRED
                        ? TransactionStatus.EXPIRED : TransactionStatus.CANCELLED);
                paymentTransactionRepository.save(transaction);
            }
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        items.sort(Comparator.comparing(item -> item.getVariant().getId()));
        for (OrderItem item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0
                    || variantRepository.restoreStock(item.getVariant().getId(), item.getQuantity()) != 1) {
                throw new IllegalStateException("Không thể hoàn tồn kho cho đơn hàng này.");
            }
        }
        if (order.getVoucher() != null) {
            voucherService.releaseVoucher(order.getVoucher().getId());
            voucherService.restoreCustomerVoucher(order.getCustomer().getId(), order.getVoucher().getId());
        }
        String historyReason = "Hủy đơn: " + reason;
        saveHistory(order, previous, OrderStatus.CANCELLED,
                historyReason.substring(0, Math.min(1000, historyReason.length())), actor);
        log.info("Order {} cancelled with type {}", order.getOrderCode(), cancelType);
    }

    private void validateCheckout(CheckoutSubmitDto request) {
        if (request == null || (request.getPaymentMethod() != PaymentMethod.COD
                && request.getPaymentMethod() != PaymentMethod.VNPAY)) {
            throw new IllegalArgumentException("Vui lòng chọn phương thức thanh toán hợp lệ.");
        }
        requireText(request.getRecipientName(), 150, "Tên người nhận");
        requireText(request.getRecipientPhone(), 20, "Số điện thoại");
        requireText(request.getShippingProvince(), 100, "Tỉnh/Thành phố");
        requireText(request.getShippingDistrict(), 100, "Quận/Huyện");
        requireText(request.getShippingWard(), 100, "Phường/Xã");
        requireText(request.getShippingAddressLine(), 300, "Địa chỉ chi tiết");
        if (request.getCustomerNote() != null && request.getCustomerNote().length() > 1000) {
            throw new IllegalArgumentException("Ghi chú không được vượt quá 1000 ký tự.");
        }
        if (request.getVoucherCode() != null && request.getVoucherCode().trim().length() > 50) {
            throw new IllegalArgumentException("Mã voucher không được vượt quá 50 ký tự.");
        }
    }

    private void requireText(String text, int maximum, String field) {
        if (text == null || text.isBlank() || text.trim().length() > maximum) {
            throw new IllegalArgumentException(field + " không được để trống hoặc vượt quá " + maximum + " ký tự.");
        }
    }

    private void validateAmount(BigDecimal amount, String message) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalStateException(message);
        }
    }

    private String cancellationReason(String reason, String fallback) {
        String result = trimToNull(reason);
        if (result != null && result.length() > 1000) {
            throw new IllegalArgumentException("Lý do hủy không được vượt quá 1000 ký tự.");
        }
        return result != null ? result : fallback;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
