package com.petshop.entity.enums;

/**
 * Payment processing status.
 */
public enum PaymentStatus {
    UNPAID,    // Initial payment state for new orders
    PAID,      // Payment collected successfully
    FAILED,    // Payment transaction failed
    CANCELLED  // Payment aborted / order cancelled
}
