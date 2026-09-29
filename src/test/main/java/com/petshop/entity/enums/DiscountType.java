package com.petshop.entity.enums;

/**
 * Voucher discount calculation mechanism.
 */
public enum DiscountType {
    PERCENT, // Percentage discount (e.g. 10 for 10%, max 100)
    FIXED    // Fixed amount discount in VNĐ (e.g. 50000 for 50,000 VNĐ)
}
