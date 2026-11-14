package com.petshop.config;

import com.petshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Release reservations after each persisted gateway deadline, even without a callback. */
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PendingPaymentExpiryScheduler {
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void expirePendingPayments() {
        orderService.cancelExpiredPendingPaymentOrders();
    }
}
