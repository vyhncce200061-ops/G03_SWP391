package com.petshop.checkout;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.cart.CartDto;
import com.petshop.dto.order.CheckoutQuoteDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "vnpay.tmn-code=", "vnpay.hash-secret=", "vnpay.return-url=",
        "petshop.free-shipping-threshold=300000",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.datasource.url=jdbc:h2:mem:checkoutworkflow;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CheckoutWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired com.petshop.config.VnPayProperties vnPayProperties;
    @Autowired CartService carts;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired VoucherRepository vouchers;
    @Autowired OrderRepository orders;
    @Autowired com.petshop.service.VoucherService voucherService;
    @Autowired UserAddressRepository addresses;
    private User customer;
    private CustomUserDetails principal;
    private ProductVariant first;
    private ProductVariant second;
    private Long firstItem;
    private Long secondItem;

    @BeforeEach
    void setup() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Role role = roles.findByCode("CUSTOMER").orElseGet(() -> roles.save(Role.builder().code("CUSTOMER").name("Khách hàng").build()));
        customer = users.save(User.builder().role(role).fullName("Khách checkout")
                .email(id + "@checkout.test").phone("09" + Math.abs(id.hashCode()))
                .passwordHash("hashed").status("ACTIVE").build());
        principal = new CustomUserDetails(customer);
        Category category = categories.save(Category.builder().code("C-" + id).name("Kiểm thử").build());
        Product one = products.save(Product.builder().category(category).name("Món được chọn " + id)
                .slug("selected-" + id).createdBy(customer).build());
        Product two = products.save(Product.builder().category(category).name("Món không chọn " + id)
                .slug("unselected-" + id).createdBy(customer).build());
        first = variants.save(ProductVariant.builder().product(one).skuCode("A-" + id).variantName("Nhỏ")
                .price(new BigDecimal("100000")).stockQuantity(10).build());
        second = variants.save(ProductVariant.builder().product(two).skuCode("B-" + id).variantName("Lớn")
                .price(new BigDecimal("250000")).stockQuantity(10).build());
        carts.addItemToCart(customer.getId(), first.getId(), 2);
        CartDto cart = carts.addItemToCart(customer.getId(), second.getId(), 1);
        firstItem = cart.getItems().stream().filter(i -> i.getVariantId().equals(first.getId())).findFirst().orElseThrow().getId();
        secondItem = cart.getItems().stream().filter(i -> i.getVariantId().equals(second.getId())).findFirst().orElseThrow().getId();
        carts.updateItemSelection(customer.getId(), secondItem, false);
    }

    @Test
    void checkoutRendersOnlySelectedRowsAndServerTotals() throws Exception {
        MvcResult result = mvc.perform(get("/checkout").with(user(principal)))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout")).andReturn();
        CheckoutQuoteDto quote = (CheckoutQuoteDto) result.getModelAndView().getModel().get("quote");
        assertThat(quote.getItems()).extracting("variantId").containsExactly(first.getId());
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("230000");
        assertThat(result.getResponse().getContentAsString()).contains(first.getProduct().getName())
                .doesNotContain(second.getProduct().getName());
    }

    @Test
    void checkoutSelectorsExposeOnlyTheCurrentCustomersWalletAndAddresses() throws Exception {
        var own = address(customer, "OWN-ADDRESS", true);
        User other = users.save(User.builder().role(customer.getRole()).fullName("Other buyer")
                .email(UUID.randomUUID()+"@checkout.test").passwordHash("unused").status("ACTIVE").build());
        address(other,"OTHER-PRIVATE-ADDRESS",true);
        var ownVoucher = walletVoucher(customer,"OWN-");
        var otherVoucher = walletVoucher(other,"PRIVATE-");
        var result=mvc.perform(get("/checkout").with(user(principal)))
                .andExpect(status().isOk()).andExpect(model().attributeExists("walletVouchers","addresses"))
                .andReturn();
        String html=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("saved-address","wallet-voucher","OWN-ADDRESS",ownVoucher.getCode())
                .doesNotContain("OTHER-PRIVATE-ADDRESS",otherVoucher.getCode());
        assertThat(((com.petshop.dto.order.CheckoutSubmitDto)result.getModelAndView().getModel().get("checkoutForm"))
                .getShippingAddressLine()).isEqualTo(own.getAddressLine());
    }

    @Test
    void invalidPostRetainsCustomAddressAndWalletInsteadOfOverwritingWithDefault() throws Exception {
        address(customer,"DEFAULT-ADDRESS",true);
        var saved=walletVoucher(customer,"PRESERVE-");
        var result=mvc.perform(post("/checkout/place-order").param("recipientName","Người nhận")
                        .param("recipientPhone","wrong-phone").param("shippingProvince","Cần Thơ")
                        .param("shippingDistrict","Ninh Kiều").param("shippingWard","Tân An")
                        .param("paymentMethod","COD").param("shippingAddressLine","CUSTOM-ADDRESS").param("voucherCode",saved.getCode())
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("checkoutForm","recipientPhone"))
                .andExpect(model().attributeExists("walletVouchers","addresses")).andReturn();
        var form=(com.petshop.dto.order.CheckoutSubmitDto)result.getModelAndView().getModel().get("checkoutForm");
        assertThat(form.getShippingAddressLine()).isEqualTo("CUSTOM-ADDRESS");
        assertThat(form.getVoucherCode()).isEqualTo(saved.getCode());
        assertThat(result.getResponse().getContentAsString()).contains("CUSTOM-ADDRESS",saved.getCode());
    }

    private UserAddress address(User owner,String line,boolean isDefault) {
        return addresses.saveAndFlush(UserAddress.builder().customer(owner).recipientName("Người nhận")
                .recipientPhone("0901234567").province("Cần Thơ").district("Ninh Kiều").ward("Tân An")
                .addressLine(line).isDefault(isDefault).build());
    }

    private Voucher walletVoucher(User owner,String prefix) {
        var saved=vouchers.saveAndFlush(Voucher.builder().code(prefix+UUID.randomUUID().toString().substring(0,8).toUpperCase())
                .title("Saved voucher").discountType(DiscountType.FIXED).discountValue(new BigDecimal("10000"))
                .minOrderAmount(BigDecimal.ZERO).startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1)).totalUsageLimit(20).perCustomerLimit(2).status(VoucherStatus.ACTIVE).build());
        voucherService.saveVoucherToWallet(owner.getId(),saved.getId());return saved;
    }

    private Voucher fixedVoucher(String code, String value) {
        return vouchers.saveAndFlush(Voucher.builder().code(code.toUpperCase(java.util.Locale.ROOT)).title("Giảm tiền hàng")
                .discountType(DiscountType.FIXED).discountValue(new BigDecimal(value)).minOrderAmount(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
                .totalUsageLimit(20).perCustomerLimit(1).status(VoucherStatus.ACTIVE).build());
    }

    @ParameterizedTest
    @CsvSource({"299999,50000,30000,279999", "300000,50000,0,250000",
            "300001,50000,0,250001", "40000,40000,30000,30000"})
    void shippingThresholdAndVoucherCapMatchInCartQuoteAndPlacedOrder(int subtotal, int discount, int fee, int total) throws Exception {
        first.setPrice(BigDecimal.valueOf(subtotal));
        variants.saveAndFlush(first);
        carts.updateItemQuantity(customer.getId(), firstItem, 1);
        var voucher = fixedVoucher("PRICE-" + UUID.randomUUID(), "50000");
        var cart = carts.getCartForUser(customer.getId());
        assertThat(cart.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(subtotal));
        assertThat(cart.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(fee));
        mvc.perform(post("/checkout/quote").with(user(principal)).with(csrf()).param("code", voucher.getCode()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(subtotal))
                .andExpect(jsonPath("$.shippingFee").value(fee)).andExpect(jsonPath("$.discountAmount").value(discount))
                .andExpect(jsonPath("$.totalAmount").value(total));
        mvc.perform(post("/checkout/apply-voucher").with(user(principal)).with(csrf()).param("code", voucher.getCode()))
                .andExpect(jsonPath("$.finalTotal").value(subtotal - discount))
                .andExpect(jsonPath("$.shippingFee").value(fee)).andExpect(jsonPath("$.totalAmount").value(total));
        mvc.perform(validOrderPost().param("voucherCode", voucher.getCode()).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/checkout/success/*"));
        var placed = orders.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(placed.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(discount));
        assertThat(placed.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(fee));
        assertThat(placed.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(total));
    }

    @Test
    void freeShippingAppliesWithoutAVoucherAndOnlyToSelectedItems() throws Exception {
        assertThat(carts.getCartForUser(customer.getId()).getShippingFee()).isEqualByComparingTo("30000");
        carts.updateItemQuantity(customer.getId(), firstItem, 3);
        var html = mvc.perform(get("/checkout").with(user(principal))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("300.000 đ tiền hàng trước voucher", "id=\"quote-shipping\">Miễn phí");
        mvc.perform(validOrderPost().with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/checkout/success/*"));
        var placed = orders.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(placed.getVoucher()).isNull();
        assertThat(placed.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(placed.getShippingFee()).isEqualByComparingTo("0");
        assertThat(placed.getTotalAmount()).isEqualByComparingTo("300000");
    }

    @Test
    void legacyFreeShippingCannotBeListedAppliedReservedOrRestoredEvenOnAnUnmigratedDatabase() throws Exception {
        var legacy = fixedVoucher(Voucher.RETIRED_SHIPPING_CODE, "30000");
        assertThat(vouchers.findAvailablePublicVouchers(LocalDateTime.now())).noneMatch(v -> v.getId().equals(legacy.getId()));
        mvc.perform(post("/checkout/quote").with(user(principal)).with(csrf()).param("code", " freeship30k "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkoutAllowed").value(false))
                .andExpect(jsonPath("$.discountAmount").value(0)).andExpect(jsonPath("$.shippingFee").value(30000));
        assertThat(vouchers.reserveVoucherAtomic(legacy.getId(), LocalDateTime.now())).isZero();
        voucherService.softDeleteVoucher(legacy.getId());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> voucherService.restoreDeletedVoucher(legacy.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("đã ngừng áp dụng");
    }

    @Test
    void freeShippingChangesTheSignedGatewayAmountAndZeroTotalCanUseCod() throws Exception {
        carts.updateItemQuantity(customer.getId(), firstItem, 3);
        var partial = fixedVoucher("VNP-" + UUID.randomUUID(), "50000");
        var full = fixedVoucher("FULL-" + UUID.randomUUID(), "300000");
        String oldCode = vnPayProperties.getTmnCode(), oldSecret = vnPayProperties.getHashSecret(), oldReturn = vnPayProperties.getReturnUrl();
        try {
            vnPayProperties.setTmnCode("TESTONLY");
            vnPayProperties.setHashSecret("synthetic-test-key");
            vnPayProperties.setReturnUrl("https://example.test/payment/vnpay-return");
            long before = orders.count();
            mvc.perform(validOrderPost(PaymentMethod.VNPAY).param("voucherCode", full.getCode()).with(user(principal)).with(csrf()))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("checkoutError"));
            assertThat(orders.count()).isEqualTo(before);
            assertThat(vouchers.findById(full.getId()).orElseThrow().getUsedCount()).isZero();
            var result = mvc.perform(validOrderPost(PaymentMethod.VNPAY)
                    .param("voucherCode", partial.getCode()).with(user(principal)).with(csrf()))
                    .andExpect(status().is3xxRedirection()).andReturn().getResponse();
            assertThat(result.getRedirectedUrl()).contains("vnp_Amount=25000000&");
            carts.addItemToCart(customer.getId(), first.getId(), 3);
            mvc.perform(validOrderPost(PaymentMethod.COD).param("voucherCode", full.getCode()).with(user(principal)).with(csrf()))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/checkout/success/*"));
            assertThat(orders.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), org.springframework.data.domain.PageRequest.of(0, 2))
                    .getContent()).anySatisfy(o -> {
                        assertThat(o.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
                        assertThat(o.getTotalAmount()).isEqualByComparingTo("0");
                        assertThat(o.getShippingFee()).isEqualByComparingTo("0");
                    });
        } finally {
            vnPayProperties.setTmnCode(oldCode);
            vnPayProperties.setHashSecret(oldSecret);
            vnPayProperties.setReturnUrl(oldReturn);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "future", "expired"})
    void voucherChangedAfterPreviewCannotBeUsedToPlaceAnOrder(String state) throws Exception {
        var voucher = fixedVoucher("RECHECK-" + UUID.randomUUID(), "50000");
        mvc.perform(post("/checkout/quote").with(user(principal)).with(csrf()).param("code", voucher.getCode()))
                .andExpect(jsonPath("$.discountAmount").value(50000));
        switch (state) {
            case "inactive" -> voucher.setStatus(VoucherStatus.INACTIVE);
            case "future" -> {
                voucher.setStartDate(LocalDateTime.now().plusDays(1));
                voucher.setEndDate(LocalDateTime.now().plusDays(2));
            }
            case "expired" -> voucher.setEndDate(LocalDateTime.now().minusHours(1));
            default -> throw new IllegalStateException(state);
        }
        vouchers.saveAndFlush(voucher);
        mvc.perform(post("/checkout/quote").with(user(principal)).with(csrf()).param("code", voucher.getCode()))
                .andExpect(jsonPath("$.checkoutAllowed").value(false)).andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(230000));
        long before = orders.count();
        mvc.perform(validOrderPost().param("voucherCode", voucher.getCode()).with(user(principal)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("checkoutError"));
        assertThat(orders.count()).isEqualTo(before);
        assertThat(vouchers.findById(voucher.getId()).orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void noSelectionKeepsSummaryButBlocksPostWithoutCreatingOrder() throws Exception {
        carts.updateItemSelection(customer.getId(), firstItem, false);
        mvc.perform(post("/checkout/quote").with(user(principal)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.shippingFee").value(0)).andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.checkoutAllowed").value(false));
        long before = orders.count();
        mvc.perform(validOrderPost().with(user(principal)).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"));
        assertThat(orders.count()).isEqualTo(before);
        assertThat(carts.getCartForUser(customer.getId()).getItems()).hasSize(2);
    }

    @Test
    void previewIncludesVoucherAndCodCheckoutKeepsUnselectedCartCount() throws Exception {
        String code = "OFF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        vouchers.save(Voucher.builder().code(code).title("Giảm thử").discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("50000")).minOrderAmount(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
                .totalUsageLimit(100).usedCount(0).perCustomerLimit(1).build());
        mvc.perform(post("/checkout/quote").with(user(principal)).with(csrf()).param("code", code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discountAmount").value(50000))
                .andExpect(jsonPath("$.totalAmount").value(180000));
        MvcResult result = mvc.perform(validOrderPost().param("voucherCode", code).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/checkout/success/*"))
                .andReturn();
        assertThat(result.getRequest().getSession().getAttribute("cartCount")).isEqualTo(1);
        CartDto remaining = carts.getCartForUser(customer.getId());
        assertThat(remaining.getItems()).extracting("variantId").containsExactly(second.getId());
        Order order = orders.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), org.springframework.data.domain.PageRequest.of(0,10)).getContent().get(0);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("180000");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
    }

    @Test
    void malformedAddressAndUnconfiguredVnpayDoNotCreateOrders() throws Exception {
        long before = orders.count();
        mvc.perform(validOrderPost().param("shippingProvince", "x".repeat(101)).with(user(principal)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("checkoutForm", "shippingProvince"));
        mvc.perform(post("/checkout/place-order").with(user(principal)).with(csrf())
                .param("recipientName", "Người nhận").param("recipientPhone", "0901234567")
                .param("shippingProvince", "Hồ Chí Minh").param("shippingDistrict", "Quận 1")
                .param("shippingWard", "Bến Nghé").param("shippingAddressLine", "12 Đường A")
                .param("paymentMethod", "VNPAY"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("checkoutError"));
        assertThat(orders.count()).isEqualTo(before);
    }

    @Test
    void cartTemplateHasCheckboxesAutomaticQuantityAndPersistentSummary() throws Exception {
        String html = mvc.perform(get("/cart").with(user(principal))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("cart.js", "checkbox", "cart-summary").doesNotContain("bi-arrow-repeat");
    }

    @Test
    void successRouteRejectsMissingOrderInsteadOfEchoingAnyCode() throws Exception {
        mvc.perform(get("/checkout/success/ORD-NOT-REAL").with(user(principal)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void fractionalStoredPriceCannotCreateOrderOrConsumeCartStock() throws Exception {
        first.setPrice(new BigDecimal("100000.25"));
        variants.saveAndFlush(first);
        long before = orders.count();
        String oldCode = vnPayProperties.getTmnCode();
        String oldSecret = vnPayProperties.getHashSecret();
        String oldReturn = vnPayProperties.getReturnUrl();
        try {
            vnPayProperties.setTmnCode("TESTONLY");
            vnPayProperties.setHashSecret("local-test-only-signing-key");
            vnPayProperties.setReturnUrl("https://example.test/payment/vnpay-return");
            mvc.perform(post("/checkout/place-order").with(user(principal)).with(csrf())
                    .param("recipientName", "Người nhận").param("recipientPhone", "0901234567")
                    .param("shippingProvince", "Hồ Chí Minh").param("shippingDistrict", "Quận 1")
                    .param("shippingWard", "Bến Nghé").param("shippingAddressLine", "12 Đường A")
                    .param("paymentMethod", "VNPAY"))
                    .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                    .andExpect(model().attributeExists("checkoutError"));
            assertThat(orders.count()).isEqualTo(before);
            assertThat(variants.findById(first.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
            assertThat(carts.getCartForUser(customer.getId()).getItems()).hasSize(2);
        } finally {
            vnPayProperties.setTmnCode(oldCode);
            vnPayProperties.setHashSecret(oldSecret);
            vnPayProperties.setReturnUrl(oldReturn);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validOrderPost() {
        return validOrderPost(PaymentMethod.COD);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validOrderPost(PaymentMethod method) {
        return post("/checkout/place-order").param("recipientName", "Người nhận")
                .param("recipientPhone", "0901234567").param("shippingProvince", "Hồ Chí Minh")
                .param("shippingDistrict", "Quận 1").param("shippingWard", "Bến Nghé")
                .param("shippingAddressLine", "12 Đường A").param("paymentMethod", method.name());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = PaymentMethod.class, names = {"COD", "VNPAY"})
    void roundedPercentageQuoteMatchesThePersistedOrderAndSignedGatewayAmount(PaymentMethod method) throws Exception {
        first.setPrice(new BigDecimal("18000.00"));variants.saveAndFlush(first);
        carts.updateItemQuantity(customer.getId(), firstItem, 1);
        String code="ROUND-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        voucherService.createVoucher(com.petshop.dto.voucher.VoucherRequestDto.builder().code(code).title("Rounding test")
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("3.33")).minOrderAmount(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
                .totalUsageLimit(10).perCustomerLimit(1).build());
        String oldCode=vnPayProperties.getTmnCode(), oldSecret=vnPayProperties.getHashSecret(), oldReturn=vnPayProperties.getReturnUrl();
        try {
            vnPayProperties.setTmnCode("TESTONLY");vnPayProperties.setHashSecret("synthetic-test-key");
            vnPayProperties.setReturnUrl("https://example.test/payment/vnpay-return");
            mvc.perform(post("/checkout/quote").with(user(principal)).with(csrf()).param("code",code))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.discountAmount").value(599))
                    .andExpect(jsonPath("$.totalAmount").value(47401));
            var response=mvc.perform(validOrderPost(method).param("voucherCode",code)
                            .with(user(principal)).with(csrf())).andExpect(status().is3xxRedirection()).andReturn().getResponse();
            var placed=orders.findByCustomerIdOrderByCreatedAtDesc(customer.getId(),org.springframework.data.domain.PageRequest.of(0,1)).getContent().get(0);
            assertThat(placed.getDiscountAmount()).isEqualByComparingTo("599");
            assertThat(placed.getTotalAmount()).isEqualByComparingTo("47401");
            assertThat(placed.getStockDeducted()).isEqualTo(method == PaymentMethod.VNPAY);
            if(method==PaymentMethod.VNPAY) {
                assertThat(response.getRedirectedUrl()).startsWith("https://sandbox.vnpayment.vn/").contains("vnp_Amount=4740100&");
                assertThat(placed.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            } else {
                assertThat(response.getRedirectedUrl()).startsWith("/checkout/success/");
                assertThat(placed.getOrderStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
            }
        } finally {
            vnPayProperties.setTmnCode(oldCode);vnPayProperties.setHashSecret(oldSecret);vnPayProperties.setReturnUrl(oldReturn);
        }
    }
}
