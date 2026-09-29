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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

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

    @Override
    @Transactional
    public Order checkoutOrder(Long customerId, CheckoutSubmitDto request) {
        log.info("Processing checkout for customer ID: {}", customerId);

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Khách hàng không tồn tại: " + customerId));

        Cart cart = cartRepository.findByUserIdWithItems(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Giỏ hàng rỗng."));

        List<CartItem> selectedItems = cart.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsSelected()))
                .toList();

        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 sản phẩm để thanh toán.");
        }

        // 1. Calculate Subtotal & Validate Product/Variant Active Status
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem ci : selectedItems) {
            ProductVariant variant = ci.getVariant();
            if (variant.getProduct().getStatus() != ProductStatus.ACTIVE 
                    || variant.getStatus() != ProductVariantStatus.ACTIVE) {
                throw new IllegalStateException("Sản phẩm '" + variant.getProduct().getName() + " - " 
                        + variant.getVariantName() + "' hiện không còn kinh doanh hoặc đã bị ẩn.");
            }
            BigDecimal itemTotal = variant.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            subtotal = subtotal.add(itemTotal);
        }

        // 2. Validate Voucher if provided
        Voucher voucher = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        String voucherCodeSnapshot = null;

        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            VoucherApplyResultDto vResult = voucherService.validateAndCalculateDiscount(
                    request.getVoucherCode().trim(), customerId, subtotal);
            if (vResult.isValid()) {
                voucher = voucherRepository.findById(vResult.getVoucherId()).orElse(null);
                discountAmount = vResult.getDiscountAmount();
                voucherCodeSnapshot = vResult.getVoucherCode();
                voucherService.reserveVoucher(vResult.getVoucherId());
            } else {
                throw new IllegalArgumentException("Mã voucher không hợp lệ: " + vResult.getMessage());
            }
        }

        BigDecimal shippingFee = new BigDecimal("30000");
        BigDecimal totalAmount = subtotal.subtract(discountAmount).add(shippingFee);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        // 3. Determine Initial State
        OrderStatus initialStatus = (request.getPaymentMethod() == PaymentMethod.VNPAY)
                ? OrderStatus.PENDING_PAYMENT
                : OrderStatus.PENDING_CONFIRMATION;

        String orderCode = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + (int) (Math.random() * 900 + 100);

        Order order = Order.builder()
                .orderCode(orderCode)
                .customer(customer)
                .voucher(voucher)
                .voucherCodeSnapshot(voucherCodeSnapshot)
                .recipientName(request.getRecipientName().trim())
                .recipientPhone(request.getRecipientPhone().trim())
                .shippingProvince(request.getShippingProvince().trim())
                .shippingDistrict(request.getShippingDistrict().trim())
                .shippingWard(request.getShippingWard().trim())
                .shippingAddressLine(request.getShippingAddressLine().trim())
                .customerNote(request.getCustomerNote())
                .subtotalAmount(subtotal)
                .discountAmount(discountAmount)
                .shippingFee(shippingFee)
                .totalAmount(totalAmount)
                .orderStatus(initialStatus)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        Order savedOrder = orderRepository.save(order);

        // 4. Create OrderItems & Atomically Deduct Stock
        List<Long> purchasedVariantIds = new ArrayList<>();
        for (CartItem ci : selectedItems) {
            ProductVariant variant = ci.getVariant();
            int qty = ci.getQuantity();

            int updated = variantRepository.deductStockAtomic(variant.getId(), qty);
            if (updated == 0) {
                throw new IllegalStateException("Sản phẩm '" + variant.getProduct().getName() + " - " 
                        + variant.getVariantName() + "' đã hết hàng hoặc không đủ số lượng trong kho.");
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .variant(variant)
                    .productNameSnapshot(variant.getProduct().getName())
                    .skuCodeSnapshot(variant.getSkuCode())
                    .variantNameSnapshot(variant.getVariantName())
                    .unitPrice(variant.getPrice())
                    .quantity(qty)
                    .build();

            orderItemRepository.save(orderItem);
            purchasedVariantIds.add(variant.getId());
        }

        // 5. Audit History
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(savedOrder)
                .oldStatus(null)
                .newStatus(initialStatus)
                .reason("Khách hàng tạo đơn hàng mới (" + request.getPaymentMethod() + ")")
                .changedBy(customer)
                .build();
        statusHistoryRepository.save(history);

        // 6. Create Payment Transaction if VNPay
        if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .transactionCode("TXN-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase())
                    .order(savedOrder)
                    .customer(customer)
                    .paymentMethod(PaymentMethod.VNPAY)
                    .paymentEnvironment(PaymentEnvironment.SANDBOX)
                    .amount(totalAmount)
                    .transactionStatus(TransactionStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .build();
            paymentTransactionRepository.save(transaction);
        }

        // 7. Clear purchased items from Cart
        cartItemRepository.deletePurchasedItems(cart.getId(), purchasedVariantIds);

        log.info("Order {} successfully created with ID: {}", orderCode, savedOrder.getId());
        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getCustomerOrders(Long customerId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByCustomerIdAndOrderStatusOrderByCreatedAtDesc(customerId, status, pageable);
        }
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderDetailForCustomer(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + orderId));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Bạn không có quyền truy cập đơn hàng này.");
        }
        return order;
    }

    @Override
    @Transactional
    public void customerCancelOrder(Long orderId, Long customerId, String reason) {
        Order order = getOrderDetailForCustomer(orderId, customerId);

        if (order.getOrderStatus() != OrderStatus.PENDING_CONFIRMATION && order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Đơn hàng đã được xác nhận hoặc đang xử lý, không thể tự hủy.");
        }

        performOrderCancellation(order, CancelType.CUSTOMER_CANCEL, reason, order.getCustomer());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getOrdersForStaff(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByOrderStatusOrderByCreatedAtDesc(status, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderDetailForStaff(Long orderId) {
        return orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + orderId));
    }

    @Override
    @Transactional
    public void confirmOrder(Long orderId, Long staffId) {
        Order order = getOrderDetailForStaff(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Chỉ có thể xác nhận đơn hàng đang ở trạng thái chờ xác nhận (PENDING_CONFIRMATION). Trạng thái hiện tại: " + order.getOrderStatus());
        }
        User staff = userRepository.findById(staffId).orElse(null);
        transitionStatus(order, OrderStatus.CONFIRMED, "Nhân viên xác nhận đơn hàng", staff);
    }

    @Override
    @Transactional
    public void markPreparing(Long orderId, Long staffId) {
        Order order = getOrderDetailForStaff(orderId);
        if (order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ có thể đóng gói đơn hàng đang ở trạng thái đã xác nhận (CONFIRMED). Trạng thái hiện tại: " + order.getOrderStatus());
        }
        User staff = userRepository.findById(staffId).orElse(null);
        transitionStatus(order, OrderStatus.PREPARING, "Đang đóng gói hàng tại kho", staff);
    }

    @Override
    @Transactional
    public void markShipping(Long orderId, Long staffId) {
        Order order = getOrderDetailForStaff(orderId);
        if (order.getOrderStatus() != OrderStatus.PREPARING) {
            throw new IllegalStateException("Chỉ có thể giao vận đơn hàng đang ở trạng thái chuẩn bị hàng (PREPARING). Trạng thái hiện tại: " + order.getOrderStatus());
        }
        User staff = userRepository.findById(staffId).orElse(null);
        transitionStatus(order, OrderStatus.SHIPPING, "Đã giao cho đơn vị vận chuyển", staff);
    }

    @Override
    @Transactional
    public void markCompleted(Long orderId, Long staffId) {
        Order order = getOrderDetailForStaff(orderId);
        if (order.getOrderStatus() != OrderStatus.SHIPPING) {
            throw new IllegalStateException("Chỉ có thể hoàn tất đơn hàng đang ở trạng thái đang giao hàng (SHIPPING). Trạng thái hiện tại: " + order.getOrderStatus());
        }
        User staff = userRepository.findById(staffId).orElse(null);

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
        Order order = getOrderDetailForStaff(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Chỉ có thể từ chối đơn hàng đang ở trạng thái chờ xác nhận (PENDING_CONFIRMATION). Trạng thái hiện tại: " + order.getOrderStatus());
        }
        User staff = userRepository.findById(staffId).orElse(null);
        performOrderCancellation(order, CancelType.STAFF_REJECT, rejectReason.trim(), staff);
    }

    @Override
    @Transactional
    public void cancelExpiredPendingPaymentOrders() {
        List<PaymentTransaction> expiredTxns = paymentTransactionRepository.findExpiredPendingTransactions(LocalDateTime.now());
        for (PaymentTransaction txn : expiredTxns) {
            txn.setTransactionStatus(TransactionStatus.EXPIRED);
            paymentTransactionRepository.save(txn);

            Order order = txn.getOrder();
            if (order.getOrderStatus() == OrderStatus.PENDING_PAYMENT) {
                performOrderCancellation(order, CancelType.PAYMENT_EXPIRED, "Hết thời gian 15 phút thanh toán trực tuyến", null);
            }
        }
    }

    private void transitionStatus(Order order, OrderStatus newStatus, String reason, User changedBy) {
        OrderStatus oldStatus = order.getOrderStatus();
        order.setOrderStatus(newStatus);
        orderRepository.save(order);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .reason(reason)
                .changedBy(changedBy)
                .build();
        statusHistoryRepository.save(history);
    }

    private void performOrderCancellation(Order order, CancelType cancelType, String reason, User actor) {
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Đơn hàng " + order.getOrderCode() + " đã bị hủy trước đó. Không thể hủy lại.");
        }
        if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Đơn hàng " + order.getOrderCode() + " đã hoàn tất. Không thể hủy đơn hàng đã hoàn tất.");
        }

        OrderStatus oldStatus = order.getOrderStatus();
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelType(cancelType);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(actor);

        if (order.getPaymentStatus() == PaymentStatus.UNPAID) {
            order.setPaymentStatus(PaymentStatus.CANCELLED);
        }

        orderRepository.save(order);

        // 1. Rollback Stock
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            variantRepository.restoreStock(item.getVariant().getId(), item.getQuantity());
        }

        // 2. Rollback Voucher
        if (order.getVoucher() != null) {
            voucherService.releaseVoucher(order.getVoucher().getId());
        }

        // 3. Audit History
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .oldStatus(oldStatus)
                .newStatus(OrderStatus.CANCELLED)
                .reason("Hủy đơn: " + (reason != null ? reason : cancelType.name()))
                .changedBy(actor)
                .build();
        statusHistoryRepository.save(history);

        log.info("Order {} was cancelled with reason: {}", order.getOrderCode(), reason);
    }
}
