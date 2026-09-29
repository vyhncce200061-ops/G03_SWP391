package com.petshop.service;

import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.entity.Order;
import com.petshop.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    Order checkoutOrder(Long customerId, CheckoutSubmitDto request);

    Page<Order> getCustomerOrders(Long customerId, OrderStatus status, Pageable pageable);
    Order getOrderDetailForCustomer(Long orderId, Long customerId);
    void customerCancelOrder(Long orderId, Long customerId, String reason);

    Page<Order> getOrdersForStaff(OrderStatus status, Pageable pageable);
    Order getOrderDetailForStaff(Long orderId);
    void confirmOrder(Long orderId, Long staffId);
    void markPreparing(Long orderId, Long staffId);
    void markShipping(Long orderId, Long staffId);
    void markCompleted(Long orderId, Long staffId);
    void staffRejectOrder(Long orderId, Long staffId, String rejectReason);
    void cancelExpiredPendingPaymentOrders();
}
