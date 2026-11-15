package com.petshop;

import com.petshop.config.ShippingFeeConfig;
import com.petshop.dto.catalog.ProductVariantFormDto;
import com.petshop.dto.voucher.VoucherRequestDto;
import com.petshop.entity.Voucher;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.impl.VoucherServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VndMoneyValidationTests {
    @ParameterizedTest @CsvSource({"18000,true", "18000.00,true", "18000.50,false", "-1,false"})
    void skuValidationAcceptsStoredWholeDongAndRejectsFractionalSellingPrices(String price, boolean valid) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var form = ProductVariantFormDto.builder().skuCode("VND-SKU").variantName("Test SKU")
                    .price(new BigDecimal(price)).status(ProductVariantStatus.ACTIVE).build();
            assertThat(factory.getValidator().validate(form).isEmpty()).isEqualTo(valid);
        }
    }

    @Test void shippingConfigurationRejectsFractionalAndNegativeFees() {
        assertThat(new ShippingFeeConfig(new BigDecimal("30000.00")).getShippingFee()).isEqualByComparingTo("30000");
        assertThatThrownBy(() -> new ShippingFeeConfig(new BigDecimal("30000.50"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShippingFeeConfig(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @CsvSource({"3.33,18000,599", "3.35,18000,603", "5,19999,1000", "0.01,1,0", "100,18000,18000"})
    void percentageDiscountRoundsOnceToWholeDong(String percent, String subtotal, String expected) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var repo = mock(VoucherRepository.class);
            var service = new VoucherServiceImpl(repo,mock(OrderRepository.class),mock(CustomerVoucherRepository.class),
                    mock(UserRepository.class),mock(EntityManager.class),factory.getValidator());
            var voucher = voucher(percent);
            when(repo.findByCode("VND-TEST")).thenReturn(Optional.of(voucher));
            var result = service.validateAndCalculateDiscount("VND-TEST",null,new BigDecimal(subtotal));
            assertThat(result.isValid()).isTrue();
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(expected);
            assertThat(result.getFinalTotal()).isEqualByComparingTo(new BigDecimal(subtotal).subtract(new BigDecimal(expected)));
            voucher.setMaxDiscountAmount(new BigDecimal("500.00"));
            if (new BigDecimal(expected).compareTo(new BigDecimal("500"))>0)
                assertThat(service.validateAndCalculateDiscount("VND-TEST",null,new BigDecimal(subtotal)).getDiscountAmount()).isEqualByComparingTo("500");
        }
    }

    @Test void fixedDiscountAndMonetaryCapsCannotIntroduceFractionalDong() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var repo=mock(VoucherRepository.class);
            var service=new VoucherServiceImpl(repo,mock(OrderRepository.class),mock(CustomerVoucherRepository.class),
                    mock(UserRepository.class),mock(EntityManager.class),factory.getValidator());
            var form=VoucherRequestDto.builder().code("VND-TEST").title("Money validation")
                    .discountType(DiscountType.FIXED).discountValue(new BigDecimal("500.50")).minOrderAmount(BigDecimal.ZERO)
                    .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
                    .totalUsageLimit(10).perCustomerLimit(1).build();
            assertThatThrownBy(() -> service.createVoucher(form)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nguyên đồng");
            verifyNoInteractions(repo);
            form.setDiscountValue(new BigDecimal("500.00"));form.setMinOrderAmount(new BigDecimal("1000.50"));
            assertThat(factory.getValidator().validate(form)).isNotEmpty();
            form.setMinOrderAmount(BigDecimal.ZERO);form.setMaxDiscountAmount(new BigDecimal("499.50"));
            assertThat(factory.getValidator().validate(form)).isNotEmpty();
            form.setMaxDiscountAmount(new BigDecimal("499.00"));
            assertThat(factory.getValidator().validate(form)).isEmpty();
        }
    }

    private Voucher voucher(String percent) {
        return Voucher.builder().id(1L).code("VND-TEST").title("Test percentage")
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal(percent)).minOrderAmount(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
                .status(VoucherStatus.ACTIVE).usedCount(0).totalUsageLimit(10).perCustomerLimit(1).build();
    }
}
