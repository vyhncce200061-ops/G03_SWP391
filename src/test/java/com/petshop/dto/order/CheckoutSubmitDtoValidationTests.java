package com.petshop.dto.order;

import com.petshop.entity.enums.PaymentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutSubmitDtoValidationTests {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void prepareValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void ordinaryAndInternationalPhonesAreAccepted() {
        CheckoutSubmitDto request = validRequest();
        assertThat(validator.validate(request)).isEmpty();
        request.setRecipientPhone("+84 901 234 567");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void dbBoundedFieldsRejectOversizedInputBeforePersistence() {
        CheckoutSubmitDto request = validRequest();
        request.setRecipientName("x".repeat(151));
        request.setShippingProvince("x".repeat(101));
        request.setShippingDistrict("x".repeat(101));
        request.setShippingWard("x".repeat(101));
        request.setShippingAddressLine("x".repeat(301));
        request.setCustomerNote("x".repeat(1001));
        request.setVoucherCode("x".repeat(51));
        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("recipientName", "shippingProvince", "shippingDistrict", "shippingWard",
                        "shippingAddressLine", "customerNote", "voucherCode");
    }

    @Test
    void requiredDistrictAndPaymentAndInvalidPhoneAreRejected() {
        CheckoutSubmitDto request = validRequest();
        request.setShippingDistrict("  ");
        request.setPaymentMethod(null);
        request.setRecipientPhone("abcdefg");
        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("shippingDistrict", "paymentMethod", "recipientPhone");
    }

    private CheckoutSubmitDto validRequest() {
        return CheckoutSubmitDto.builder().recipientName("Khách hàng").recipientPhone("0901234567")
                .shippingProvince("TP. Hồ Chí Minh").shippingDistrict("Quận 1").shippingWard("Bến Nghé")
                .shippingAddressLine("123 Đường A").paymentMethod(PaymentMethod.COD).build();
    }
}
