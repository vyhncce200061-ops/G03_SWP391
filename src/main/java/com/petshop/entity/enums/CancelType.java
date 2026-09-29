package com.petshop.entity.enums;

/**
 * Categorization of order cancellation cause.
 */
public enum CancelType {
    CUSTOMER_CANCEL,   // Customer cancelled while in PENDING_*
    STAFF_REJECT,      // Staff rejected during confirmation (requires CancelReason)
    PAYMENT_FAILED,    // Online payment returned failure code
    PAYMENT_CANCELLED, // Customer aborted at gateway checkout
    PAYMENT_EXPIRED    // 15-minute background job cleaned up expired pending transaction
}
