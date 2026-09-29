package com.petshop.entity.enums;

/**
 * Order state machine lifecycle states.
 */
public enum OrderStatus {
    PENDING_CONFIRMATION, // Initial state for COD orders
    PENDING_PAYMENT,      // Initial state for VNPay orders
    CONFIRMED,            // Accepted by staff or VNPay IPN success
    PREPARING,            // Warehouse packing items
    SHIPPING,             // Carrier in transit
    COMPLETED,            // Delivery successful & COD collected
    CANCELLED             // Order cancelled / expired / rejected
}
