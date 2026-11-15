package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:feedbackReport;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class FeedbackReportWorkflowTests {
    @MockBean EmailService mail;
    @Autowired MockMvc mvc;
    @Autowired RoleRepository roles; @Autowired UserRepository users;
    @Autowired CategoryRepository categories; @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants; @Autowired ProductImageRepository images;
    @Autowired OrderRepository orders; @Autowired OrderItemRepository items; @Autowired ReviewRepository reviewRows;
    @Autowired ReviewService reviews; @Autowired ReportService reports;
    @Autowired AdminDashboardService dashboard; @Autowired ProductThumbnailService thumbnails;
    @Autowired jakarta.persistence.EntityManager em;
    User admin,customer,otherCustomer; Product product,otherProduct; ProductVariant variant,otherVariant;
    String key; int sequence;
    @BeforeEach void fixture() {
        key=UUID.randomUUID().toString().replace("-","").substring(0,10);
        admin=account("ADMIN");customer=account("CUSTOMER");otherCustomer=account("CUSTOMER");
        var category=categories.saveAndFlush(Category.builder().code("FEEDBACK-"+key).name("Feedback").build());
        product=products.saveAndFlush(Product.builder().name("Current product "+key).slug("feedback-"+key).category(category).createdBy(admin).build());
        otherProduct=products.saveAndFlush(Product.builder().name(product.getName()).slug("other-feedback-"+key).category(category).createdBy(admin).build());
        variant=variants.saveAndFlush(ProductVariant.builder().product(product).skuCode("FB-"+key).variantName("Current variant").price(new BigDecimal("100000")).stockQuantity(10).build());
        otherVariant=variants.saveAndFlush(ProductVariant.builder().product(otherProduct).skuCode("FB-OTHER-"+key).variantName("Other variant").price(new BigDecimal("100000")).stockQuantity(10).build());
    }
    User account(String code) {
        var role=roles.findByCode(code).orElseGet(()->roles.saveAndFlush(Role.builder().code(code).name(code).build()));
        return users.saveAndFlush(User.builder().role(role).email(code.toLowerCase()+UUID.randomUUID()+"@feedback.test").fullName(code+" "+key).passwordHash("unused-hash").build());
    }
    Order order(LocalDateTime completed,OrderStatus status,PaymentStatus paid,String subtotal,String discount) {
        var goods=new BigDecimal(subtotal);var reduction=new BigDecimal(discount);var shipping=new BigDecimal("30000");
        return orders.saveAndFlush(Order.builder().orderCode("FB-"+key+"-"+(++sequence)).customer(customer).recipientName("Demo customer")
                .recipientPhone("0900000001").shippingProvince("Cần Thơ").shippingDistrict("Ninh Kiều").shippingWard("Tân An").shippingAddressLine("123 Demo")
                .subtotalAmount(goods).discountAmount(reduction).shippingFee(shipping).totalAmount(goods.subtract(reduction).add(shipping))
                .paymentMethod(PaymentMethod.COD).paymentStatus(paid).orderStatus(status).completedAt(completed).build());
    }
    OrderItem item(ProductVariant sku,LocalDateTime completed) {
        var order=order(completed,OrderStatus.COMPLETED,PaymentStatus.PAID,"100000","0");
        return items.saveAndFlush(OrderItem.builder().order(order).variant(sku).productNameSnapshot("Purchased product "+sku.getId())
                .variantNameSnapshot("Purchased variant").skuCodeSnapshot(sku.getSkuCode()).quantity(1).unitPrice(new BigDecimal("100000")).build());
    }
    Review review(ProductVariant sku,int stars,ReviewStatus visibility) {
        var item=item(sku,LocalDateTime.now().minusHours(1));
        return reviewRows.saveAndFlush(Review.builder().orderItem(item).customer(customer).rating(stars).comment("Review for "+sku.getSkuCode()).status(visibility).build());
    }
    Map<String,Object> report(String preset) throws Exception {
        return mvc.perform(get("/admin/reports/revenue").with(user(new CustomUserDetails(admin))).param("preset",preset))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
    }
    @Test void dashboardAndAllTimeReportUseTheSameNetRevenueAndKeepShippingSeparate() throws Exception {
        order(LocalDateTime.now().minusDays(60),OrderStatus.COMPLETED,PaymentStatus.PAID,"300000","30000");
        order(LocalDateTime.now().minusHours(1),OrderStatus.COMPLETED,PaymentStatus.PAID,"120000","0");
        var result=report("all");
        assertThat((BigDecimal)result.get("totalRevenue")).isEqualByComparingTo("390000");
        assertThat(dashboard.getDashboardStats().getTotalRevenue()).isEqualByComparingTo((BigDecimal)result.get("totalRevenue"));
        assertThat((BigDecimal)result.get("totalShipping")).isEqualByComparingTo("60000");
        assertThat((BigDecimal)result.get("totalCollected")).isEqualByComparingTo("450000");
        assertEquals(2L,result.get("totalOrders"));
        assertThat((BigDecimal)report("30days").get("totalRevenue")).isEqualByComparingTo("120000");
    }
    @Test void unpaidIncompleteMissingCompletionAndFutureOrdersDoNotInflateRevenue() throws Exception {
        order(LocalDateTime.now(),OrderStatus.COMPLETED,PaymentStatus.PAID,"100000","10000");
        order(LocalDateTime.now(),OrderStatus.COMPLETED,PaymentStatus.UNPAID,"999999","0");
        order(null,OrderStatus.CONFIRMED,PaymentStatus.PAID,"888888","0");
        order(null,OrderStatus.COMPLETED,PaymentStatus.PAID,"777777","0");
        order(LocalDate.now().plusDays(1).atStartOfDay(),OrderStatus.COMPLETED,PaymentStatus.PAID,"666666","0");
        order(LocalDateTime.now(),OrderStatus.CANCELLED,PaymentStatus.PAID,"555555","0");
        assertThat(dashboard.getDashboardStats().getTotalRevenue()).isEqualByComparingTo("90000");
        assertThat((BigDecimal)report("all").get("totalRevenue")).isEqualByComparingTo("90000");
        assertThat((BigDecimal)report("today").get("totalRevenue")).isEqualByComparingTo("90000");
    }
    @Test void revenueDateRangeIsInclusiveAtStartAndExclusiveAtNextMidnight() throws Exception {
        LocalDate day=LocalDate.of(2026,1,31);
        order(day.atStartOfDay(),OrderStatus.COMPLETED,PaymentStatus.PAID,"100000","0");
        order(day.atTime(23,59,59),OrderStatus.COMPLETED,PaymentStatus.PAID,"200000","0");
        order(day.plusDays(1).atStartOfDay(),OrderStatus.COMPLETED,PaymentStatus.PAID,"900000","0");
        var result=mvc.perform(get("/admin/reports/revenue").with(user(new CustomUserDetails(admin)))
                .param("startDate",day.toString()).param("endDate",day.toString()).param("groupBy","month"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat((BigDecimal)result.get("totalRevenue")).isEqualByComparingTo("300000");
        assertEquals(2L,result.get("totalOrders"));
    }
    @Test void emptyAllTimeReportsHaveZeroTotalsAndValidDateRanges() throws Exception {
        assertThat((BigDecimal)report("all").get("totalRevenue")).isZero();
        mvc.perform(get("/admin/reports/reviews").with(user(new CustomUserDetails(admin))).param("preset","all"))
                .andExpect(status().isOk()).andExpect(model().attribute("reviewCount",0L));
    }
    @Test void thumbnailSelectionPrefersPrimarySkipsBlankAndFallsBackWithoutImages() {
        images.saveAndFlush(ProductImage.builder().product(product).imageUrl("/images/secondary.png").sortOrder(0).isPrimary(false).build());
        images.saveAndFlush(ProductImage.builder().product(product).imageUrl(" ").sortOrder(0).isPrimary(true).build());
        images.saveAndFlush(ProductImage.builder().product(product).imageUrl("/images/primary.png").sortOrder(10).isPrimary(true).build());
        var item=item(variant,LocalDateTime.now());
        assertEquals("/images/primary.png",thumbnails.forProducts(List.of(product.getId())).get(product.getId()));
        assertEquals("/images/primary.png",thumbnails.forOrderItems(List.of(item.getId())).get(item.getId()));
        assertEquals(ProductThumbnailService.PLACEHOLDER,thumbnails.forProducts(List.of(otherProduct.getId())).get(otherProduct.getId()));
        assertTrue(thumbnails.forOrderItems(List.of()).isEmpty());
    }
    @Test void reviewTotalsAreWeightedByReviewCountAndSeparateProductsWithTheSameName() throws Exception {
        review(variant,5,ReviewStatus.VISIBLE);review(variant,5,ReviewStatus.HIDDEN);review(otherVariant,1,ReviewStatus.VISIBLE);
        var result=mvc.perform(get("/admin/reports/reviews").with(user(new CustomUserDetails(admin))).param("preset","all"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertEquals(3L,result.get("reviewCount"));assertEquals(2L,result.get("visibleReviewCount"));assertEquals(1L,result.get("hiddenReviewCount"));
        assertThat((BigDecimal)result.get("averageReviewRating")).isEqualByComparingTo("3.7");
        assertEquals(2,((List<?>)result.get("reviewStats")).size());
    }
    @Test void multipleImagesDoNotMultiplyPaginatedReviewsOrTheirReportCounts() {
        review(variant,5,ReviewStatus.VISIBLE);review(variant,3,ReviewStatus.VISIBLE);
        images.saveAndFlush(ProductImage.builder().product(product).imageUrl("/images/a.png").isPrimary(true).build());
        images.saveAndFlush(ProductImage.builder().product(product).imageUrl("/images/b.png").isPrimary(false).build());
        var page=reviews.myReviews(customer.getId(),PageRequest.of(0,1));
        assertEquals(2,page.getTotalElements());assertEquals(1,page.getContent().size());
        assertEquals(2,reports.reviewsByProduct(LocalDate.now().atStartOfDay(),LocalDate.now().plusDays(1).atStartOfDay()).get(0).getReviewCount());
    }
    @Test void hiddenReviewStillMarksPurchasedItemReviewedAndNeverLeaksToAnotherCustomer() {
        var saved=review(variant,5,ReviewStatus.HIDDEN);Long id=saved.getOrderItem().getId();
        assertEquals(Set.of(id),reviews.reviewedItemIds(customer.getId(),List.of(id)));
        assertTrue(reviews.reviewedItemIds(otherCustomer.getId(),List.of(id)).isEmpty());
        assertThrows(IllegalArgumentException.class,()->reviews.eligibleItem(customer.getId(),id));
    }
    @Test void composeFormFetchesRequiredProductRelationsAndShowsExactPurchaseContext() throws Exception {
        var purchased=item(variant,LocalDateTime.now().minusHours(1));
        em.clear();
        var eligible=reviews.eligibleItem(customer.getId(),purchased.getId());
        assertTrue(Hibernate.isInitialized(eligible.getVariant().getProduct()));
        String body=mvc.perform(get("/customer/reviews/create").with(user(new CustomUserDetails(customer))).param("orderItemId",purchased.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains(purchased.getProductNameSnapshot(),purchased.getVariantNameSnapshot(),purchased.getOrder().getOrderCode(),
                "/images/product-placeholder.svg","type=\"radio\"","name=\"rating\"","maxlength=\"1000\"");
    }
    @Test void customerHistoryAndAdminReplyShowPurchasedNamesAndProductImages() throws Exception {
        var saved=review(variant,5,ReviewStatus.VISIBLE);
        images.saveAndFlush(ProductImage.builder().product(product).imageUrl("/images/primary.png").isPrimary(true).build());
        for(String route:List.of("/customer/reviews","/admin/reviews")){
            var principal=route.startsWith("/admin")?admin:customer;
            String body=mvc.perform(get(route).with(user(new CustomUserDetails(principal)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).contains(saved.getOrderItem().getProductNameSnapshot(),"/images/primary.png",saved.getOrderItem().getOrder().getOrderCode())
                    .doesNotContain("Current variant");
        }
    }

    @Test void standaloneSuccessReturnsToHistoryInsteadOfReopeningTheNowReviewedItem() throws Exception {
        var purchased=item(variant,LocalDateTime.now().minusHours(1));
        mvc.perform(post("/customer/reviews/create").with(user(new CustomUserDetails(customer))).with(csrf())
                .header("Referer","http://localhost/customer/reviews/create?orderItemId="+purchased.getId())
                .param("orderItemId",purchased.getId().toString()).param("rating","4").param("comment","Sản phẩm đúng phân loại đã mua."))
                .andExpect(redirectedUrl("/customer/reviews"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("errorMessage",org.hamcrest.Matchers.nullValue()));
        assertEquals(1,reviewRows.count());
    }
}
