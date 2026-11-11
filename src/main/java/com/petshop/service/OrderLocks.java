package com.petshop.service;

import com.petshop.entity.Order;
import com.petshop.repository.OrderRepository;
import com.petshop.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** All mutations serialize a customer's orders before locking order/SKU/voucher rows. */
@Component
@RequiredArgsConstructor
public class OrderLocks {
    private final OrderRepository orders;
    private final UserRepository users;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Order> findForUpdate(Long orderId) {
        if (orderId == null) return Optional.empty();
        var existing = entityManager.find(Order.class, orderId);
        if (existing == null) return Optional.empty();
        // Nested settlement must retain unflushed PAID/stock changes in this transaction.
        if (entityManager.getLockMode(existing) == LockModeType.PESSIMISTIC_WRITE) return Optional.of(existing);
        var customer = users.findByIdForUpdate(existing.getCustomer().getId()).orElseThrow(
                () -> new IllegalStateException("Khách hàng của đơn không còn tồn tại."));
        entityManager.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        var locked = orders.findByIdForUpdate(orderId);
        // Open-in-view may contain a snapshot loaded before a competing transaction committed.
        locked.ifPresent(order -> entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE));
        return locked;
    }
}
