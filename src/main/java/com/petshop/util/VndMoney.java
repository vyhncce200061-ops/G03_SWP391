package com.petshop.util;

import java.math.BigDecimal;

public final class VndMoney {
    private VndMoney() { }

    public static boolean isWhole(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= 0;
    }

    public static void requireNonNegativeWhole(BigDecimal value, String field) {
        if (!isWhole(value) || value.signum() < 0) {
            throw new IllegalArgumentException(field + " phải là số nguyên đồng và không được âm.");
        }
    }
}
