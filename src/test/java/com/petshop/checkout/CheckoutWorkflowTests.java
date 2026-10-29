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
    void paymentUrlFailureRollsBackOrderAndStockInsteadOfLeavingAnUnpayableOrder() throws Exception {
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
        return post("/checkout/place-order").param("recipientName", "Người nhận")
                .param("recipientPhone", "0901234567").param("shippingProvince", "Hồ Chí Minh")
                .param("shippingDistrict", "Quận 1").param("shippingWard", "Bến Nghé")
                .param("shippingAddressLine", "12 Đường A").param("paymentMethod", "COD");
    }
}
