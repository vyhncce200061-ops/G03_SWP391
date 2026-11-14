package com.petshop.config;

import java.math.BigDecimal;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class ShippingFeeConfig {
    private final BigDecimal shippingFee;

    public ShippingFeeConfig(@Value("${petshop.shipping-fee:30000}") BigDecimal shippingFee) {
        com.petshop.util.VndMoney.requireNonNegativeWhole(shippingFee, "Phí giao hàng");
        this.shippingFee = shippingFee;
    }
}
