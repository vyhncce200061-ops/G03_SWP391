package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.admin.*;
import com.petshop.dto.voucher.*;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:datScopeAudit;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DatScopeAuditTests {
    @MockBean EmailService mail;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserService accounts;
    @Autowired VoucherService vouchers;
    @Autowired VoucherRepository voucherRows;
    @Autowired ReviewService reviews;
    @Autowired OrderService orders;
    @Autowired OrderRepository orderRows;
    @Autowired PlatformTransactionManager transactions;
    User admin,staff,customer;
    String key;
    @BeforeEach void fixture(){
        key=UUID.randomUUID().toString().replace("-","").substring(0,12);
        admin=account("ADMIN");staff=account("STAFF");customer=account("CUSTOMER");
    }
    User account(String role){
        var savedRole=roles.findByCode(role).orElseGet(()->roles.save(Role.builder().code(role).name(role).build()));
        return users.save(User.builder().role(savedRole).fullName(role+key).email(role.toLowerCase(Locale.ROOT)+key+"@scope.test")
                .passwordHash("unused-hash").build());
    }
    VoucherRequestDto voucherForm(){return VoucherRequestDto.builder().code("AUDIT"+key.toUpperCase(Locale.ROOT)).title("Audit voucher")
            .discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN).minOrderAmount(BigDecimal.ZERO)
            .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
            .totalUsageLimit(10).perCustomerLimit(1).status(VoucherStatus.ACTIVE).build();}

    @Test void accountUpdateRejectsPipeAsPhoneDigit() throws Exception {
        mvc.perform(post("/admin/users/"+customer.getId()+"/edit").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("id",customer.getId().toString()).param("fullName","Valid customer").param("email",customer.getEmail())
                .param("phone","0|12345678").param("status","ACTIVE"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("userForm","phone"));
        assertNull(users.findById(customer.getId()).orElseThrow().getPhone());
    }

    @Test void invalidVoucherEditRetainsPathIdAndEditFormAction() throws Exception {
        var saved=vouchers.createVoucher(voucherForm());
        String path="/admin/vouchers/"+saved.getId()+"/edit";
        String html=mvc.perform(post(path).with(user(new CustomUserDetails(admin))).with(csrf()).param("code",saved.getCode())
                .param("id","-1").param("title","").param("discountType","PERCENT").param("discountValue","10")
                .param("minOrderAmount","0").param("startDate","2026-11-01T00:00").param("endDate","2026-12-01T00:00")
                .param("totalUsageLimit","10").param("perCustomerLimit","1").param("status","ACTIVE"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("voucherForm","title"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("action=\""+path+"\""),"Validation errors must return to the voucher being edited");
        assertEquals("Audit voucher",voucherRows.findById(saved.getId()).orElseThrow().getTitle());
    }

    @Test void invalidAccountEditRetainsPathIdAndEditFormAction() throws Exception {
        String path="/admin/users/"+customer.getId()+"/edit";
        String html=mvc.perform(post(path).with(user(new CustomUserDetails(admin))).with(csrf()).param("id","-1")
                .param("fullName","").param("status","ACTIVE"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("userForm","fullName"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("action=\""+path+"\""));
    }

    @Test void oversizedPageOffsetsReturnEmptyPagesInsteadOfServerErrors() throws Exception {
        for(String route:List.of("/admin/users","/admin/vouchers","/admin/reviews","/staff/orders","/admin/reports/product-sales")){
            mvc.perform(get(route).with(user(new CustomUserDetails(admin))).param("page",Integer.toString(Integer.MAX_VALUE))
                    .param("size","100")).andExpect(status().isOk());
        }
        mvc.perform(get("/customer/reviews").with(user(new CustomUserDetails(customer))).param("page",Integer.toString(Integer.MAX_VALUE)))
                .andExpect(status().isOk());
    }

    @Test void dateArithmeticOverflowReportsFriendlyValidationInsteadOfServerErrors() throws Exception {
        for(String route:List.of("/admin/reports/reviews","/admin/reports/product-sales")){
            mvc.perform(get(route).with(user(new CustomUserDetails(admin))).param("endDate","-999999999-01-01"))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        }
    }

    @Test void missingOrderDetailReturnsNotFoundInsteadOfServerError() throws Exception {
        mvc.perform(get("/staff/orders/"+Long.MAX_VALUE).with(user(new CustomUserDetails(admin)))).andExpect(status().isNotFound());
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired CategoryRepository categories;
    @Autowired CartService carts;
    @Autowired StockImportService imports;
    @Autowired OrderItemRepository orderItems;
    @Autowired ReviewRepository reviewRows;
    @Autowired ReportService reports;
    Product product;
    ProductVariant variant;

    void stock(int quantity) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            var cat=categories.save(Category.builder().name("Scope "+key).code("S"+key).build());
            product=products.save(Product.builder().name("Product "+key).slug("scope-"+key).category(cat).createdBy(admin).petType(PetType.DOG).build());
            variant=variants.save(ProductVariant.builder().product(product).skuCode("SKU"+key).variantName("Small")
                    .stockQuantity(quantity).price(new BigDecimal("100000")).build());
        });
    }
    Order purchase(User buyer,int quantity,String code) {
        carts.addItemToCart(buyer.getId(),variant.getId(),quantity);
        return orders.checkoutOrder(buyer.getId(),com.petshop.dto.order.CheckoutSubmitDto.builder().recipientName("Buyer")
                .recipientPhone("0901234567").shippingProvince("HCM").shippingDistrict("Thu Duc").shippingWard("Linh Trung")
                .shippingAddressLine("123 Test").paymentMethod(PaymentMethod.COD).voucherCode(code).build());
    }
    void complete(Order order) {
        orders.confirmOrder(order.getId(),staff.getId());orders.markPreparing(order.getId(),staff.getId());
        orders.markShipping(order.getId(),staff.getId());orders.markCompleted(order.getId(),staff.getId());
    }
    List<Boolean> race(java.util.concurrent.Callable<Boolean> first,java.util.concurrent.Callable<Boolean> second)throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {var a=pool.submit(()->{gate.await();return first.call();});var b=pool.submit(()->{gate.await();return second.call();});
            gate.countDown();return List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
    }

    @Test void invalidAccountFormsAndDuplicateContactsCannotMutateUsers() throws Exception {
        for(String field:List.of("fullName","email","phone")) {
            String name=field.equals("fullName")?" ":"Staff test";
            String email=field.equals("email")?"invalid":"newstaff"+key+"@scope.test";
            String phone=field.equals("phone")?"123":"0901234567";
            mvc.perform(post("/admin/users/create").with(user(new CustomUserDetails(admin))).with(csrf())
                    .param("fullName",name).param("email",email).param("phone",phone))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("staffForm",field));
        }
        assertFalse(users.existsByEmail("newstaff"+key+"@scope.test"));
        var dto=UserAdminUpdateDto.builder().fullName("Changed name").email(admin.getEmail()).status("ACTIVE").build();
        assertThrows(IllegalArgumentException.class,()->accounts.updateUserByAdmin(customer.getId(),dto,admin.getId()));
        assertEquals(customer.getFullName(),users.findById(customer.getId()).orElseThrow().getFullName());
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test void mailFailureFromCreateFormLeavesNoAccountOrPasswordLink() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Không thể gửi email mời.")).when(mail)
                .sendStaffInvitation(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        String email="failed"+key+"@scope.test";
        mvc.perform(post("/admin/users/create").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("fullName","Staff test").param("email",email).param("phone","0901234567"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertFalse(users.existsByEmail(email));
    }

    @Test void simultaneousAdminLocksKeepOneActiveAdmin() throws Exception {
        var other=account("ADMIN");
        jdbc.update("UPDATE dbo.Users SET Status='LOCKED' WHERE RoleId=? AND Id NOT IN (?,?)",admin.getRole().getId(),admin.getId(),other.getId());
        var result=race(()->{try{accounts.toggleUserStatus(other.getId(),admin.getId());return true;}catch(IllegalArgumentException e){return false;}},
                ()->{try{accounts.toggleUserStatus(admin.getId(),other.getId());return true;}catch(IllegalArgumentException e){return false;}});
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertEquals(1,users.countByRoleCodeAndStatus("ADMIN","ACTIVE"));
    }

    @Test void voucherCalculationRespectsAmountCapsDatesAndDeletion() {
        var form=voucherForm();form.setDiscountValue(new BigDecimal("50"));form.setMaxDiscountAmount(new BigDecimal("20000"));
        form.setMinOrderAmount(new BigDecimal("100000"));var v=vouchers.createVoucher(form);
        assertFalse(vouchers.validateAndCalculateDiscount(v.getCode(),customer.getId(),new BigDecimal("99999.99")).isValid());
        var result=vouchers.validateAndCalculateDiscount(v.getCode(),customer.getId(),new BigDecimal("100000"));
        assertTrue(result.isValid());assertEquals(0,new BigDecimal("20000").compareTo(result.getDiscountAmount()));
        vouchers.softDeleteVoucher(v.getId());assertFalse(vouchers.validateAndCalculateDiscount(v.getCode(),customer.getId(),new BigDecimal("100000")).isValid());
        vouchers.restoreDeletedVoucher(v.getId());assertFalse(vouchers.validateAndCalculateDiscount(v.getCode(),customer.getId(),new BigDecimal("100000")).isValid());
        vouchers.toggleVoucherStatus(v.getId());assertTrue(vouchers.validateAndCalculateDiscount(v.getCode(),customer.getId(),new BigDecimal("100000")).isValid());
        form.setDiscountType(DiscountType.FIXED);form.setDiscountValue(new BigDecimal("200000"));form.setMaxDiscountAmount(null);
        vouchers.updateVoucher(v.getId(),form);
        assertEquals(0,new BigDecimal("100000").compareTo(vouchers.validateAndCalculateDiscount(v.getCode(),customer.getId(),new BigDecimal("100000")).getDiscountAmount()));
    }

    @Test void concurrentOrdersCannotConsumeTheLastVoucherTwice() throws Exception {
        stock(10);var other=account("CUSTOMER");var form=voucherForm();form.setTotalUsageLimit(1);var v=vouchers.createVoucher(form);
        carts.addItemToCart(customer.getId(),variant.getId(),1);carts.addItemToCart(other.getId(),variant.getId(),1);
        var request=com.petshop.dto.order.CheckoutSubmitDto.builder().recipientName("Buyer").recipientPhone("0901234567")
                .shippingProvince("HCM").shippingDistrict("Thu Duc").shippingWard("Linh Trung").shippingAddressLine("123 Test")
                .paymentMethod(PaymentMethod.COD).voucherCode(v.getCode()).build();
        var result=race(()->{try{orders.checkoutOrder(customer.getId(),request);return true;}catch(IllegalArgumentException|IllegalStateException e){return false;}},
                ()->{try{orders.checkoutOrder(other.getId(),request);return true;}catch(IllegalArgumentException|IllegalStateException e){return false;}});
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertEquals(1,voucherRows.findById(v.getId()).orElseThrow().getUsedCount());
        assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test void stockReportOnlyCountsAllocatedOrdersAndDoesNotInventMissingCost() {
        stock(0);
        imports.createStockImport(com.petshop.dto.stock.StockImportFormDto.builder().supplierName("Supplier")
                .items(List.of(com.petshop.dto.stock.StockImportItemDto.builder().variantId(variant.getId()).quantity(10).unitCost(new BigDecimal("20000")).build())).build(),staff.getId());
        var order=purchase(customer,2,null);
        var row=reports.stockReconciliation().stream().filter(x->x.getSku().equals(variant.getSkuCode())).findFirst().orElseThrow();
        assertEquals(10,row.getCurrentStock());assertEquals(0,row.getReservedOrSold());assertEquals(0,row.getDifference());
        assertEquals(0,new BigDecimal("200000").compareTo(row.getEstimatedValue()));
        assertTrue(reports.productSales(LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1),org.springframework.data.domain.PageRequest.of(0,20))
                .stream().noneMatch(x->((Number)x[1]).longValue()==product.getId()));
        orders.customerCancelOrder(order.getId(),customer.getId(),"Customer cancelled");
        var after=reports.stockReconciliation().stream().filter(x->x.getSku().equals(variant.getSkuCode())).findFirst().orElseThrow();
        assertEquals(10,after.getCurrentStock());assertEquals(0,after.getReservedOrSold());
        var allocated=purchase(customer,2,null);orders.confirmOrder(allocated.getId(),staff.getId());
        var confirmed=reports.stockReconciliation().stream().filter(x->x.getSku().equals(variant.getSkuCode())).findFirst().orElseThrow();
        assertEquals(8,confirmed.getCurrentStock());assertEquals(2,confirmed.getReservedOrSold());assertEquals(0,confirmed.getDifference());
        assertEquals(0,new BigDecimal("160000").compareTo(confirmed.getEstimatedValue()));
        jdbc.update("UPDATE dbo.ProductVariants SET StockQuantity=11 WHERE Id=?",variant.getId());
        assertNull(reports.stockReconciliation().stream().filter(x->x.getSku().equals(variant.getSkuCode())).findFirst().orElseThrow().getEstimatedValue());
    }

    @Test void revenueAndProductSalesUseCompletionBoundaryAndKeepShippingSeparate() throws Exception {
        stock(10);var v=vouchers.createVoucher(voucherForm());var jan=purchase(customer,2,v.getCode());complete(jan);
        var feb=purchase(customer,2,null);complete(feb);
        jdbc.update("UPDATE dbo.Orders SET CompletedAt=? WHERE Id=?",java.sql.Timestamp.valueOf("2026-01-31 23:59:59"),jan.getId());
        jdbc.update("UPDATE dbo.Orders SET CompletedAt=? WHERE Id=?",java.sql.Timestamp.valueOf("2026-02-01 00:00:00"),feb.getId());
        var result=mvc.perform(get("/admin/reports/revenue").with(user(new CustomUserDetails(admin)))
                .param("startDate","2026-01-01").param("endDate","2026-01-31").param("groupBy","month"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertEquals(1L,result.get("totalOrders"));assertEquals(0,new BigDecimal("180000").compareTo((BigDecimal)result.get("totalRevenue")));
        assertEquals(0,new BigDecimal("30000").compareTo((BigDecimal)result.get("totalShipping")));
        assertEquals(0,new BigDecimal("210000").compareTo((BigDecimal)result.get("totalCollected")));
        var rows=reports.productSales(LocalDateTime.of(2026,1,1,0,0),LocalDateTime.of(2026,2,1,0,0),org.springframework.data.domain.PageRequest.of(0,20));
        var row=rows.stream().filter(x->((Number)x[1]).longValue()==product.getId()).findFirst().orElseThrow();
        assertEquals(2L,((Number)row[2]).longValue());assertEquals(0,new BigDecimal("200000").compareTo(new BigDecimal(row[3].toString())));
    }

    @Test void feedbackValidatesBoundariesAndNeverExposesAnotherCustomersHistory() throws Exception {
        stock(10);var order=purchase(customer,1,null);complete(order);var item=orderItems.findByOrderId(order.getId()).get(0);
        for(int stars:List.of(0,6)) {
            mvc.perform(post("/customer/reviews/create").with(user(new CustomUserDetails(customer))).with(csrf())
                    .param("orderItemId",item.getId().toString()).param("rating",Integer.toString(stars)).param("comment","Valid feedback"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/customer/reviews"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }
        var f=new com.petshop.dto.ReviewCreateDto();f.setRating(5);
        for(String bad:List.of("", "x".repeat(1001), " ".repeat(10))){f.setComment(bad);assertThrows(IllegalArgumentException.class,()->reviews.createReview(customer.getId(),item.getId(),f));}
        f.setComment("Unique "+key+" "+"x".repeat(980-key.length()));var review=reviews.createReview(customer.getId(),item.getId(),f);
        assertFalse(reviews.myReviews(staff.getId(),org.springframework.data.domain.PageRequest.of(0,10)).hasContent());
        var other=account("CUSTOMER");String html=mvc.perform(get("/customer/reviews").with(user(new CustomUserDetails(other)))
                .param("customerId",customer.getId().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("Unique "+key));
        reviews.toggleReviewStatus(review.getId());assertTrue(reviews.publicReviews(product.getId(),org.springframework.data.domain.PageRequest.of(0,10)).isEmpty());
        assertEquals(1,reviews.myReviews(customer.getId(),org.springframework.data.domain.PageRequest.of(0,10)).getTotalElements());
    }

    @Test void reviewModerationKeepsPageSizeAndFilterWhenRedirecting() throws Exception {
        stock(10);var order=purchase(customer,1,null);complete(order);var item=orderItems.findByOrderId(order.getId()).get(0);
        var f=new com.petshop.dto.ReviewCreateDto();f.setRating(5);f.setComment("Filter "+key);var review=reviews.createReview(customer.getId(),item.getId(),f);
        String location=mvc.perform(post("/admin/reviews/"+review.getId()+"/toggle-status").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("keyword","A&B").param("rating","5").param("status","VISIBLE").param("page","2").param("size","20"))
                .andExpect(flash().attributeExists("successMessage")).andReturn().getResponse().getRedirectedUrl();
        assertTrue(location.contains("size=20"));assertTrue(location.contains("keyword=A%26B"));
        assertEquals(ReviewStatus.HIDDEN,reviewRows.findById(review.getId()).orElseThrow().getStatus());
    }

    @Test void allOwnedWriteEndpointsEnforceRolesAndCsrf() throws Exception {
        List<String> adminOnly=List.of("/admin/users/create","/admin/users/"+customer.getId()+"/edit","/admin/users/"+customer.getId()+"/toggle-status",
                "/admin/users/"+staff.getId()+"/resend-invitation","/admin/vouchers/create","/admin/vouchers/1/edit","/admin/vouchers/1/delete",
                "/admin/vouchers/1/restore","/admin/vouchers/1/toggle-status","/admin/reviews/1/toggle-status","/admin/reviews/1/reply");
        for(String path:adminOnly){
            for(var actor:List.of(staff,customer))mvc.perform(post(path).with(user(new CustomUserDetails(actor))).with(csrf())).andExpect(redirectedUrl("/error/403"));
            mvc.perform(post(path).with(user(new CustomUserDetails(admin)))).andExpect(redirectedUrl("/error/403"));
        }
        for(String path:List.of("/admin/users","/admin/vouchers","/admin/reviews","/admin/reports/revenue","/admin/reports/product-sales","/admin/reports/stock"))
            mvc.perform(get(path)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post("/staff/orders/1/confirm").with(user(new CustomUserDetails(customer))).with(csrf())).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post("/staff/orders/1/confirm").with(user(new CustomUserDetails(admin)))).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post("/customer/reviews/create").with(user(new CustomUserDetails(staff))).with(csrf())).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post("/customer/reviews/create").with(user(new CustomUserDetails(customer)))).andExpect(redirectedUrl("/error/403"));
        org.mockito.Mockito.verifyNoInteractions(mail);
    }
}
