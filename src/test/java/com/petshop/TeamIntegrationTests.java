package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.catalog.ProductFormDto;
import com.petshop.dto.stock.StockImportFormDto;
import com.petshop.dto.stock.StockImportItemDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Deliberately no test transaction: templates must render with open-in-view disabled,
// after service transactions have ended, as they do during a real request.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teamIntegration;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeamIntegrationTests {
    @MockBean EmailService mail;
    @Autowired MockMvc mvc;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired StockImportRepository receipts;
    @Autowired ProductManageService productService;
    @Autowired StockImportService imports;
    @Autowired CartService carts;
    @Autowired CatalogService catalog;
    @Autowired UserService userService;
    @Autowired PlatformTransactionManager transactionManager;
    User admin, staff, customer;
    Category category;
    Product product;
    ProductVariant variant;
    String key;

    @BeforeEach void fixture() {
        key = UUID.randomUUID().toString();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            admin = account("ADMIN"); staff = account("STAFF"); customer = account("CUSTOMER");
            category = categories.save(Category.builder().code(key).name("Food " + key).build());
            product = products.save(Product.builder().category(category).createdBy(admin).name("Food " + key)
                    .slug(key).petType(PetType.DOG).isFeatured(true).build());
            variant = variants.save(ProductVariant.builder().product(product).skuCode(key).variantName("Small")
                    .price(new BigDecimal("200000")).stockQuantity(10).build());
        });
    }

    private User account(String code) {
        Role role = roles.findByCode(code).orElseGet(() -> roles.save(Role.builder().code(code).name(code).build()));
        return users.save(User.builder().role(role).fullName(code).email(code + key + "@integration.test")
                .passwordHash("unused-password-hash").build());
    }

    @Test void allIntegratedPagesRenderOutsideServiceTransactions() throws Exception {
        for (String path : List.of("/", "/products", "/products/" + key, "/about", "/contact", "/login")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        for (String path : List.of("/admin/products", "/admin/products/create", "/admin/products/" + product.getId() + "/edit",
                "/admin/categories", "/admin/categories/create", "/admin/brands", "/admin/reports/reviews")) {
            mvc.perform(get(path).with(user(new CustomUserDetails(admin)))).andExpect(status().isOk());
        }
        for (String path : List.of("/staff/products", "/staff/products/create", "/staff/products/" + product.getId() + "/edit",
                "/staff/products/" + product.getId() + "/variants", "/staff/products/low-stock",
                "/staff/stock-imports", "/staff/stock-imports/create")) {
            mvc.perform(get(path).with(user(new CustomUserDetails(staff)))).andExpect(status().isOk());
        }
        productService.deleteProduct(product.getId());
        mvc.perform(get("/products/" + key)).andExpect(status().isNotFound());
        assertTrue(productService.searchProducts(key, category.getId(), PageRequest.of(0, 10)).hasContent());
        assertTrue(catalog.searchAndFilterProducts(com.petshop.dto.catalog.ProductFilterDto.builder().keyword(key).build(),
                PageRequest.of(0, 10)).isEmpty());
    }

    @Test void staffAndAdminUseTheSameValidatedFormAndCannotOverwriteStock() throws Exception {
        for (String base : List.of("/staff/products", "/admin/products")) {
            User operator = base.startsWith("/staff") ? staff : admin;
            String slug = base.contains("staff") ? "staff-" + key : "admin-" + key;
            mvc.perform(post(base + "/create").with(user(new CustomUserDetails(operator)))
                    .param("name", "Blocked")).andExpect(redirectedUrl("/error/403"));
            mvc.perform(post(base + "/create").with(user(new CustomUserDetails(operator))).with(csrf())
                    .param("name", "Created " + key).param("slug", slug).param("categoryId", category.getId().toString())
                    .param("petType", "DOG").param("status", "ACTIVE")).andExpect(redirectedUrl(base));
            Product created = products.findBySlug(slug).orElseThrow();
            mvc.perform(post(base + "/" + created.getId() + "/edit").with(user(new CustomUserDetails(operator))).with(csrf())
                    .param("name", "").param("slug", slug).param("categoryId", category.getId().toString())
                    .param("petType", "DOG").param("status", "ACTIVE"))
                    .andExpect(status().isOk()).andExpect(view().name("staff/product-form"))
                    .andExpect(model().attributeHasFieldErrors("productForm", "name"));
            assertEquals("Created " + key, products.findById(created.getId()).orElseThrow().getName());
        }
        var newSku = productService.addVariant(product.getId(), ProductVariant.builder().skuCode("new-" + key)
                .variantName("Large").price(BigDecimal.TEN).stockQuantity(999).build());
        assertEquals(0, newSku.getStockQuantity());
        productService.updateVariant(variant.getId(), ProductVariant.builder().variantName("Updated")
                .price(BigDecimal.TEN).lowStockThreshold(5).status(ProductVariantStatus.ACTIVE).stockQuantity(999).build());
        assertEquals(10, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test void stockDtoImportsPersistDetailsAndInvalidLinesRollBack() throws Exception {
        StockImportFormDto form = StockImportFormDto.builder().supplierName("Supplier")
                .items(List.of(StockImportItemDto.builder().variantId(variant.getId()).quantity(3).unitCost(BigDecimal.TEN).build())).build();
        StockImport saved = imports.createStockImport(form, staff.getId());
        assertEquals(13, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(1, imports.getStockImportDetail(saved.getId()).getItems().size());
        mvc.perform(get("/staff/stock-imports/" + saved.getId()).with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(key)));
        long before = receipts.count();
        form.setItems(List.of(form.getItems().get(0), form.getItems().get(0)));
        assertThrows(IllegalArgumentException.class, () -> imports.createStockImport(form, staff.getId()));
        assertEquals(before, receipts.count());
        assertEquals(13, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test void cartSupportsAjaxAndRegularFormWithoutLosingStockRules() throws Exception {
        var cart = carts.addItemToCart(customer.getId(), variant.getId(), 1);
        String itemId = cart.getItems().get(0).getId().toString();
        mvc.perform(post("/cart/update").with(user(new CustomUserDetails(customer))).with(csrf())
                .header("X-Requested-With", "XMLHttpRequest").param("itemId", itemId).param("quantity", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(3));
        mvc.perform(post("/cart/update").with(user(new CustomUserDetails(customer))).with(csrf())
                .param("itemId", itemId).param("quantity", "2"))
                .andExpect(redirectedUrl("/cart")).andExpect(flash().attributeExists("successMessage"));
        mvc.perform(post("/cart/update").with(user(new CustomUserDetails(customer))).with(csrf())
                .header("X-Requested-With", "XMLHttpRequest").param("itemId", itemId).param("quantity", "999"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        assertEquals(2, carts.getCartForUser(customer.getId()).getTotalQuantity());
        mvc.perform(post("/cart/update").with(user(new CustomUserDetails(customer))).with(csrf())
                .param("itemId", itemId).param("quantity", "0")).andExpect(redirectedUrl("/cart"));
        assertEquals(0, carts.getCartForUser(customer.getId()).getTotalQuantity());
        assertEquals(10, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test void categoryWithoutParentAndBrandCrudRemainUsable() throws Exception {
        mvc.perform(post("/admin/categories/create").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("code", "new-" + key).param("name", "New category").param("parentCategory.id", ""))
                .andExpect(redirectedUrl("/admin/categories"));
        Category created = categories.findByCode("new-" + key).orElseThrow();
        assertNull(created.getParentCategory());
        mvc.perform(post("/admin/categories/update/" + created.getId()).with(user(new CustomUserDetails(admin))).with(csrf())
                .param("code", created.getCode()).param("name", created.getName()).param("parentCategory.id", created.getId().toString()))
                .andExpect(redirectedUrl("/admin/categories/edit/" + created.getId())).andExpect(flash().attributeExists("errorMessage"));
        assertNull(categories.findById(created.getId()).orElseThrow().getParentCategory());
        mvc.perform(get("/admin/categories/edit/" + created.getId()).with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/brands/create").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("name", "Brand-" + key)).andExpect(redirectedUrl("/admin/brands"));
        Brand brand = brands.findAll().stream().filter(b -> b.getName().equals("Brand-" + key)).findFirst().orElseThrow();
        mvc.perform(post("/admin/brands/delete/" + brand.getId()).with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/brands"));
        assertFalse(brands.existsById(brand.getId()));
    }

    @Test void uploadedProductImagesAreServedFromTheRuntimeDirectory() throws Exception {
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        String slug = "image-" + key;
        String imageUrl = null;
        try {
            mvc.perform(multipart("/staff/products/create")
                    .file(new org.springframework.mock.web.MockMultipartFile("imageFiles", "product.png", "image/png", buffer.toByteArray()))
                    .with(user(new CustomUserDetails(staff))).with(csrf())
                    .param("name", "Uploaded image").param("slug", slug).param("categoryId", category.getId().toString())
                    .param("petType", "DOG").param("status", "ACTIVE"))
                    .andExpect(redirectedUrl("/staff/products"));
            Product saved = products.findBySlug(slug).orElseThrow();
            imageUrl = productService.findById(saved.getId()).getImages().iterator().next().getImageUrl();
            assertTrue(imageUrl.matches("/uploads/products/[0-9a-f-]+\\.png"));
            mvc.perform(get(imageUrl)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/png"));
        } finally {
            if (imageUrl != null && imageUrl.matches("/uploads/products/[0-9a-f-]+\\.png")) {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(imageUrl.substring(1)));
            }
        }
        mvc.perform(multipart("/staff/products/create")
                .file(new org.springframework.mock.web.MockMultipartFile("imageFiles", "bad.png", "image/png", "not an image".getBytes()))
                .with(user(new CustomUserDetails(staff))).with(csrf())
                .param("name", "Rejected image").param("slug", "bad-" + key).param("categoryId", category.getId().toString())
                .param("petType", "DOG").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertFalse(products.existsBySlug("bad-" + key));
    }

    @Test void staffAndAdminProfileFormsUseRoutesAllowedForTheirRole() throws Exception {
        for (User actor : List.of(admin, staff)) {
            String html = mvc.perform(get("/account/profile").with(user(new CustomUserDetails(actor))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("action=\"/account/profile\""));
            assertTrue(html.contains("action=\"/account/password\""));
            assertTrue(html.contains("action=\"/account/profile/avatar\""));
            assertFalse(html.contains("action=\"/customer/"));
            assertFalse(html.contains("id=\"tab-orders-pill\""));
            assertFalse(html.contains("id=\"editAddressForm\""));
            mvc.perform(post("/account/profile").with(user(new CustomUserDetails(actor))).with(csrf())
                    .param("fullName", "Updated " + key).param("email", actor.getEmail()))
                    .andExpect(redirectedUrl("/account/profile"));
            User updated = users.findById(actor.getId()).orElseThrow();
            assertEquals("Updated " + key, updated.getFullName());
            assertEquals(actor.getRole().getCode(), updated.getRole().getCode());
        }
        mvc.perform(get("/account/profile").with(user(new CustomUserDetails(customer))))
                .andExpect(redirectedUrl("/customer/profile"));
    }

    @Test void addressEditFormsSupplyUsableCsrfTokensFromBothCustomerPages() throws Exception {
        var address = userService.addAddress(customer.getId(), com.petshop.dto.auth.AddressCreateDto.builder()
                .recipientName("Customer").recipientPhone("0901000000").province("HCM").district("Thu Duc")
                .ward("Linh Trung").addressLine("Original address").isDefault(true).build());
        for (String page : List.of("/customer/addresses", "/customer/profile")) {
            var result = mvc.perform(get(page).with(user(new CustomUserDetails(customer)))).andExpect(status().isOk()).andReturn();
            var form = java.util.regex.Pattern.compile("<form[^>]*id=\"editAddressForm\"[^>]*>(.*?)</form>", java.util.regex.Pattern.DOTALL)
                    .matcher(result.getResponse().getContentAsString());
            assertTrue(form.find(), "Address edit form must exist");
            var token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(form.group(1));
            assertTrue(token.find(), "Dynamic-action edit form needs an explicit CSRF token");
            mvc.perform(post("/customer/addresses/" + address.getId() + "/edit")
                    .with(user(new CustomUserDetails(customer)))
                    .session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false))
                    .param("_csrf", token.group(1)).param("recipientName", "Customer").param("recipientPhone", "0901000000")
                    .param("province", "HCM").param("district", "Thu Duc").param("ward", "Linh Trung")
                    .param("addressLine", "Updated from " + page))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
            assertEquals("Updated from " + page, userService.getCustomerAddresses(customer.getId()).get(0).getAddressLine());
        }
    }

    @Test void staffAndAdminCanUseTheExistingAvatarUploadWithoutCustomerAccess() throws Exception {
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        for (User actor : List.of(admin, staff)) {
            String imageUrl = null;
            try {
                mvc.perform(multipart("/account/profile/avatar")
                        .file(new org.springframework.mock.web.MockMultipartFile("avatarFile", "avatar.png", "image/png", buffer.toByteArray()))
                        .with(user(new CustomUserDetails(actor))).with(csrf()))
                        .andExpect(redirectedUrl("/account/profile")).andExpect(flash().attributeExists("successMessage"));
                imageUrl = users.findById(actor.getId()).orElseThrow().getAvatarUrl();
                assertTrue(imageUrl.matches("/uploads/avatars/avatar_[0-9]+_[0-9a-f-]+\\.png"));
                mvc.perform(get(imageUrl)).andExpect(status().isOk());
            } finally {
                if (imageUrl != null && imageUrl.matches("/uploads/avatars/avatar_[0-9]+_[0-9a-f-]+\\.png")) {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(imageUrl.substring(1)));
                }
            }
        }
    }

    @Autowired OrderService orderService;
    @Autowired OrderItemRepository orderItems;
    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired OrderRepository orderRepository;

    private Order purchasedOrder() {
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        var order=orderService.checkoutOrder(customer.getId(),com.petshop.dto.order.CheckoutSubmitDto.builder()
                .recipientName("Customer").recipientPhone("0901234567").shippingProvince("Hồ Chí Minh")
                .shippingDistrict("Thủ Đức").shippingWard("Linh Trung").shippingAddressLine("123 Test")
                .paymentMethod(PaymentMethod.COD).build());
        return order;
    }
    private void finish(Order order) {
        orderService.confirmOrder(order.getId(),admin.getId());orderService.markPreparing(order.getId(),staff.getId());
        orderService.markShipping(order.getId(),staff.getId());orderService.markCompleted(order.getId(),staff.getId());
    }
    @Test void feedbackRequiresPaidCompletedPurchaseAndRejectsDuplicateOrForeignItem() throws Exception {
        var order=purchasedOrder();var item=orderItems.findByOrderId(order.getId()).get(0);
        var form=new com.petshop.dto.ReviewCreateDto();form.setRating(5);form.setComment("Verified buyer");
        assertThrows(IllegalArgumentException.class,()->reviewService.createReview(customer.getId(),item.getId(),form));
        finish(order);
        assertThrows(IllegalArgumentException.class,()->reviewService.createReview(staff.getId(),item.getId(),form));
        var stranger=users.save(User.builder().role(customer.getRole()).fullName("Other").email("other"+key+"@integration.test").passwordHash("unused-password-hash").build());
        assertThrows(IllegalArgumentException.class,()->reviewService.createReview(stranger.getId(),item.getId(),form));
        for(int rating: List.of(0,6)) { form.setRating(rating);assertThrows(IllegalArgumentException.class,()->reviewService.createReview(customer.getId(),item.getId(),form)); }
        form.setRating(5);form.setComment("x".repeat(1001));
        assertThrows(IllegalArgumentException.class,()->reviewService.createReview(customer.getId(),item.getId(),form));
        form.setComment("<script>alert('x')</script>");
        mvc.perform(get("/customer/reviews/create").param("orderItemId",item.getId().toString()).with(user(new CustomUserDetails(customer)))).andExpect(status().isOk());
        mvc.perform(post("/customer/reviews/create").param("orderItemId",item.getId().toString()).param("rating","5").with(user(new CustomUserDetails(customer))))
                .andExpect(redirectedUrl("/error/403"));
        var review=reviewService.createReview(customer.getId(),item.getId(),form);
        assertThrows(IllegalArgumentException.class,()->reviewService.createReview(customer.getId(),item.getId(),form));
        String html=mvc.perform(get("/products/"+key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>alert('x')</script>"));
        mvc.perform(get("/admin/reviews").with(user(new CustomUserDetails(admin)))).andExpect(status().isOk());
        reviewService.toggleReviewStatus(review.getId());
        assertFalse(mvc.perform(get("/products/"+key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString().contains("&lt;script&gt;"));
        assertTrue(mvc.perform(get("/customer/reviews").with(user(new CustomUserDetails(customer)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString().contains("&lt;script&gt;"));
        assertFalse(mvc.perform(get("/customer/reviews").with(user(new CustomUserDetails(stranger)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString().contains("&lt;script&gt;"));
        mvc.perform(get("/customer/reviews").with(user(new CustomUserDetails(staff)))).andExpect(redirectedUrl("/error/403"));
    }
    @Test void concurrentFeedbackSubmissionsProduceOneReview() throws Exception {
        var order=purchasedOrder();finish(order);var item=orderItems.findByOrderId(order.getId()).get(0);
        var form=new com.petshop.dto.ReviewCreateDto();form.setRating(4);form.setComment("Khách đã mua và dùng sản phẩm");
        var gate=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> submit=()-> { gate.await();try {reviewService.createReview(customer.getId(),item.getId(),form);return true;}catch(IllegalArgumentException expected){return false;} };
        try {var first=pool.submit(submit);var second=pool.submit(submit);gate.countDown();assertNotEquals(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
        assertTrue(reviewRepository.existsByOrderItemId(item.getId()));
    }
    @Test void salesReportsFilterByCompletionAndPaymentAndOrderConfirmationIsIdempotentlyRejected() throws Exception {
        var order=purchasedOrder();
        assertThrows(IllegalArgumentException.class,()->orderService.confirmOrder(order.getId(),customer.getId()));
        mvc.perform(post("/staff/orders/"+order.getId()+"/confirm").with(user(new CustomUserDetails(customer))).with(csrf())).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post("/staff/orders/"+order.getId()+"/confirm").with(user(new CustomUserDetails(admin))).with(csrf())).andExpect(flash().attributeExists("successMessage"));
        mvc.perform(post("/staff/orders/"+order.getId()+"/confirm").with(user(new CustomUserDetails(admin))).with(csrf())).andExpect(flash().attributeExists("errorMessage"));
        orderService.markPreparing(order.getId(),staff.getId());orderService.markShipping(order.getId(),staff.getId());orderService.markCompleted(order.getId(),staff.getId());
        var result=mvc.perform(get("/admin/reports/product-sales").with(user(new CustomUserDetails(admin)))).andExpect(status().isOk()).andReturn().getModelAndView();
        var page=(org.springframework.data.domain.Page<Object[]>)result.getModel().get("sales");
        var row=page.getContent().stream().filter(r->((Number)r[1]).longValue()==product.getId()).findFirst().orElseThrow();
        assertEquals(2L,((Number)row[2]).longValue());assertEquals(0,new BigDecimal("400000").compareTo(new BigDecimal(row[3].toString())));assertEquals(1L,((Number)row[4]).longValue());
        mvc.perform(get("/admin/reports/product-sales").with(user(new CustomUserDetails(staff)))).andExpect(redirectedUrl("/error/403"));
        for(String path:List.of("/admin/reports/product-sales","/admin/reports/revenue","/admin/reports/reviews")) {
            mvc.perform(get(path).param("startDate","2026-12-30").param("endDate","2026-01-01").with(user(new CustomUserDetails(admin))))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
            mvc.perform(get(path).param("endDate","9999-12-31").with(user(new CustomUserDetails(admin))))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        }
    }

    @Test void shopReplyIsAdminOnlyValidatedSingleAndEscapedOnPublicAndPersonalViews() throws Exception {
        var order=purchasedOrder();finish(order);var item=orderItems.findByOrderId(order.getId()).get(0);
        var form=new com.petshop.dto.ReviewCreateDto();form.setRating(3);form.setComment("Feedback customer");
        var review=reviewService.createReview(customer.getId(),item.getId(),form);
        for(var actor:List.of(staff,customer)) {
            assertThrows(IllegalArgumentException.class,()->reviewService.reply(review.getId(),actor.getId(),"Reply"));
            mvc.perform(post("/admin/reviews/"+review.getId()+"/reply").param("replyText","Reply").with(user(new CustomUserDetails(actor))).with(csrf()))
                    .andExpect(redirectedUrl("/error/403"));
        }
        for(String invalid:List.of("   ","x".repeat(1001))) assertThrows(IllegalArgumentException.class,()->reviewService.reply(review.getId(),admin.getId(),invalid));
        mvc.perform(post("/admin/reviews/"+review.getId()+"/reply").param("replyText","<b>Shop reply</b>").with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(flash().attributeExists("successMessage"));
        assertThrows(IllegalArgumentException.class,()->reviewService.reply(review.getId(),admin.getId(),"Overwrite"));
        var saved=reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals(admin.getId(),saved.getRepliedBy().getId());assertNotNull(saved.getRepliedAt());assertEquals("Feedback customer",saved.getComment());
        for(String path:List.of("/products/"+key,"/customer/reviews")) {
            String html=mvc.perform(get(path).with(user(new CustomUserDetails(customer)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("&lt;b&gt;Shop reply&lt;/b&gt;"));assertFalse(html.contains("<b>Shop reply</b>"));
        }
        mvc.perform(get("/admin/reviews").with(user(new CustomUserDetails(admin)))).andExpect(status().isOk());
        reviewService.toggleReviewStatus(review.getId());
        assertFalse(mvc.perform(get("/products/"+key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString().contains("Shop reply"));
        assertTrue(mvc.perform(get("/customer/reviews").with(user(new CustomUserDetails(customer)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString().contains("Shop reply"));
    }

    @Test void replyRejectsMissingInvalidAndLockedRequestsAndKeepsListContext() throws Exception {
        var order=purchasedOrder();finish(order);var item=orderItems.findByOrderId(order.getId()).get(0);
        var form=new com.petshop.dto.ReviewCreateDto();form.setRating(2);form.setComment("Feedback for validation");
        var review=reviewService.createReview(customer.getId(),item.getId(),form);
        String path="/admin/reviews/"+review.getId()+"/reply";
        mvc.perform(post(path).param("replyText","Reply").with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(path).with(csrf()).with(user(new CustomUserDetails(admin))))
                .andExpect(flash().attributeExists("errorMessage"));
        var result=mvc.perform(post(path).param("replyText"," ").param("keyword","a&status=VISIBLE")
                .param("page","2").param("size","20").param("status","HIDDEN")
                .with(csrf()).with(user(new CustomUserDetails(admin))))
                .andExpect(flash().attributeExists("errorMessage","replyDraft","replyReviewId")).andReturn();
        assertTrue(result.getResponse().getRedirectedUrl().contains("keyword=a%26status%3DVISIBLE"));
        assertTrue(result.getResponse().getRedirectedUrl().contains("page=2"));
        mvc.perform(get("/admin/reviews").flashAttrs(result.getFlashMap()).with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk());
        assertNull(reviewRepository.findById(review.getId()).orElseThrow().getReplyText());
        assertThrows(IllegalArgumentException.class,()->reviewService.reply(-1L,admin.getId(),"Reply"));
        admin.setStatus("LOCKED");users.save(admin);
        mvc.perform(post(path).param("replyText","Reply").with(csrf()).with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/login?expired=true"));
        assertThrows(IllegalArgumentException.class,()->reviewService.reply(review.getId(),admin.getId(),"Reply"));
        admin.setStatus("ACTIVE");users.save(admin);
        mvc.perform(post(path).param("replyText","x".repeat(1000)).param("adminId",staff.getId().toString())
                .with(csrf()).with(user(new CustomUserDetails(admin))))
                .andExpect(flash().attributeExists("successMessage"));
        var saved=reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals(1000,saved.getReplyText().length());assertEquals(admin.getId(),saved.getRepliedBy().getId());
    }

    @Test void concurrentAdminsCannotOverwriteReplyAndModerationPreservesIt() throws Exception {
        var order=purchasedOrder();finish(order);var item=orderItems.findByOrderId(order.getId()).get(0);
        var form=new com.petshop.dto.ReviewCreateDto();form.setRating(4);form.setComment("Feedback concurrency");
        var review=reviewService.createReview(customer.getId(),item.getId(),form);
        var secondAdmin=users.save(User.builder().role(admin.getRole()).fullName("Second admin")
                .email("second-admin"+key+"@integration.test").passwordHash("unused").build());
        var gate=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            var first=pool.submit(()->{gate.await();try {reviewService.reply(review.getId(),admin.getId(),"First reply");return true;} catch(IllegalArgumentException expected){return false;} });
            var second=pool.submit(()->{gate.await();try {reviewService.reply(review.getId(),secondAdmin.getId(),"Second reply");return true;} catch(IllegalArgumentException expected){return false;} });
            var hide=pool.submit(()->{gate.await();reviewService.toggleReviewStatus(review.getId());return true;});
            gate.countDown();
            boolean wonFirst=first.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(wonFirst,second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(hide.get(15,java.util.concurrent.TimeUnit.SECONDS));
            var saved=reviewRepository.findById(review.getId()).orElseThrow();
            assertEquals(wonFirst ? "First reply" : "Second reply",saved.getReplyText());
            assertEquals(wonFirst ? admin.getId() : secondAdmin.getId(),saved.getRepliedBy().getId());
            assertEquals(ReviewStatus.HIDDEN,saved.getStatus());
            reviewService.toggleReviewStatus(review.getId());
            String publicHtml=mvc.perform(get("/products/"+key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(publicHtml.contains(saved.getReplyText()));
        } finally { pool.shutdownNow(); }
    }
}
