package com.petshop.checkout;

import com.petshop.config.ShippingFeeConfig;
import com.petshop.dto.cart.CartDto;
import com.petshop.dto.cart.CartItemDto;
import com.petshop.dto.order.CheckoutQuoteDto;
import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.service.CheckoutPricingService;
import com.petshop.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoFreeShippingTests {

    private ShippingFeeConfig shippingFeeConfig;
    private VoucherService voucherService;
    private CheckoutPricingService pricingService;

    @BeforeEach
    void setUp() {
        shippingFeeConfig = new ShippingFeeConfig(new BigDecimal("30000"), new BigDecimal("500000"));
        voucherService = mock(VoucherService.class);
        pricingService = new CheckoutPricingService(voucherService, shippingFeeConfig);
    }

    @Test
    @DisplayName("Đơn hàng dưới 500k tính phí giao hàng chuẩn 30k")
    void orderBelowThresholdChargesStandardShippingFee() {
        BigDecimal fee = shippingFeeConfig.calculateShippingFee(new BigDecimal("499000"));
        assertThat(fee).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Đơn hàng đạt ngưỡng 500k được miễn phí vận chuyển tự động (0đ)")
    void orderAtOrAboveThresholdGetsFreeShippingAutomatically() {
        BigDecimal feeExact = shippingFeeConfig.calculateShippingFee(new BigDecimal("500000"));
        BigDecimal feeAbove = shippingFeeConfig.calculateShippingFee(new BigDecimal("1200000"));

        assertThat(feeExact).isEqualByComparingTo("0");
        assertThat(feeAbove).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CheckoutQuoteDto tính tự động miễn phí vận chuyển và áp voucher chuẩn xác")
    void checkoutQuoteAppliesFreeShippingAndVoucher() {
        CartItemDto item = CartItemDto.builder()
                .variantId(1L)
                .productName("Hạt cho chó Royal Canin")
                .unitPrice(new BigDecimal("300000"))
                .quantity(2)
                .lineTotal(new BigDecimal("600000"))
                .isSelected(true)
                .available(true)
                .build();

        CartDto cart = CartDto.builder()
                .items(List.of(item))
                .subtotal(new BigDecimal("600000"))
                .checkoutAllowed(true)
                .selectedQuantity(2)
                .build();

        when(voucherService.validateAndCalculateDiscount(eq("DISCOUNT50"), any(), eq(new BigDecimal("600000"))))
                .thenReturn(VoucherApplyResultDto.builder()
                        .valid(true)
                        .voucherCode("DISCOUNT50")
                        .discountAmount(new BigDecimal("50000"))
                        .build());

        CheckoutQuoteDto quote = pricingService.quote(cart, 100L, "DISCOUNT50");

        // Subtotal = 600,000 >= 500,000 threshold -> Shipping Fee = 0
        assertThat(quote.getShippingFee()).isEqualByComparingTo("0");
        assertThat(quote.getDiscountAmount()).isEqualByComparingTo("50000");
        // Total = 600,000 - 50,000 + 0 = 550,000
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("550000");
    }

    @Test
    @DisplayName("CheckoutQuoteDto dưới ngưỡng vẫn tính phí giao hàng bình thường")
    void checkoutQuoteBelowThresholdIncludesShippingFee() {
        CartItemDto item = CartItemDto.builder()
                .variantId(1L)
                .productName("Đồ chơi cho mèo")
                .unitPrice(new BigDecimal("150000"))
                .quantity(1)
                .lineTotal(new BigDecimal("150000"))
                .isSelected(true)
                .available(true)
                .build();

        CartDto cart = CartDto.builder()
                .items(List.of(item))
                .subtotal(new BigDecimal("150000"))
                .checkoutAllowed(true)
                .selectedQuantity(1)
                .build();

        CheckoutQuoteDto quote = pricingService.quote(cart, 100L, null);

        // Subtotal = 150,000 < 500,000 -> Shipping Fee = 30,000
        assertThat(quote.getShippingFee()).isEqualByComparingTo("30000");
        assertThat(quote.getDiscountAmount()).isEqualByComparingTo("0");
        // Total = 150,000 + 30,000 = 180,000
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("180000");
    }
}
