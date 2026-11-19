package com.petshop.config;

import java.math.BigDecimal;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ShippingFeeConfig {
    private final BigDecimal shippingFee;
    private final BigDecimal freeShippingThreshold;

    public ShippingFeeConfig(BigDecimal shippingFee) {
        this(shippingFee, new BigDecimal("500000"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ShippingFeeConfig(
            @Value("${petshop.shipping-fee:30000}") BigDecimal shippingFee,
            @Value("${petshop.free-shipping-threshold:500000}") BigDecimal freeShippingThreshold) {
        com.petshop.util.VndMoney.requireNonNegativeWhole(shippingFee, "Phí giao hàng");
        if (freeShippingThreshold != null) {
            com.petshop.util.VndMoney.requireNonNegativeWhole(freeShippingThreshold, "Ngưỡng miễn phí giao hàng");
        }
        this.shippingFee = shippingFee;
        this.freeShippingThreshold = freeShippingThreshold;
    }

    public BigDecimal calculateShippingFee(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (freeShippingThreshold != null && freeShippingThreshold.compareTo(BigDecimal.ZERO) > 0
                && subtotal.compareTo(freeShippingThreshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return shippingFee;
    }
}
