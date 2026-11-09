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
                .param("code", "new-" + key).param("name", "New category").param("parentId", ""))
                .andExpect(redirectedUrl("/admin/categories"));
        Category created = categories.findByCode("new-" + key).orElseThrow();
        assertNull(created.getParentCategory());
        mvc.perform(post("/admin/categories/update/" + created.getId()).with(user(new CustomUserDetails(admin))).with(csrf())
                .param("code", created.getCode()).param("name", created.getName()).param("parentId", created.getId().toString()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("categoryForm"));
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

    @Test void productUploadsPreserveOriginalGifAndJpegWithSafeExtensions() throws Exception {
        for(String format:List.of("gif","jpeg")) {
            var buffer=new java.io.ByteArrayOutputStream();
            var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
            if(format.equals("gif")) {
                var writer=javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
                try(var output=javax.imageio.ImageIO.createImageOutputStream(buffer)) {
                    writer.setOutput(output);writer.prepareWriteSequence(null);
                    for(int i=0;i<2;i++){image.setRGB(0,0,i==0?0xff0000:0x0000ff);writer.writeToSequence(new javax.imageio.IIOImage(image,null,null),null);}
                    writer.endWriteSequence();
                }finally{writer.dispose();}
            }else assertTrue(javax.imageio.ImageIO.write(image,format,buffer));
            byte[] original=buffer.toByteArray();String slug=format+"-"+key,imageUrl=null;
            try {
                mvc.perform(multipart("/staff/products/create")
                        .file(new org.springframework.mock.web.MockMultipartFile("imageFiles","untrusted-name.html","image/"+format,original))
                        .with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("name","Original image").param("slug",slug).param("categoryId",category.getId().toString())
                        .param("petType","DOG").param("status","ACTIVE"))
                        .andExpect(redirectedUrl("/staff/products"));
                var saved=products.findBySlug(slug).orElseThrow();
                imageUrl=productService.findById(saved.getId()).getImages().iterator().next().getImageUrl();
                assertTrue(imageUrl.matches("/uploads/products/[0-9a-f-]+\\."+(format.equals("jpeg")?"jpg":"gif")));
                assertArrayEquals(original,java.nio.file.Files.readAllBytes(java.nio.file.Path.of(imageUrl.substring(1))));
                mvc.perform(get(imageUrl)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/"+format));
                if(format.equals("gif"))try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(original))){
                    var reader=javax.imageio.ImageIO.getImageReaders(input).next();
                    try{reader.setInput(input);assertEquals(2,reader.getNumImages(true));}finally{reader.dispose();}
                }
            }finally{
                if(imageUrl!=null&&imageUrl.matches("/uploads/products/[0-9a-f-]+\\.(jpg|gif)"))java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(imageUrl.substring(1)));
            }
        }
    }

    @Test void spoofedAndBrokenImagesReturnValidationErrorsWithoutCreatingProducts() throws Exception {
        var buffer=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",buffer);
        for(boolean broken:List.of(false,true)) {
            byte[] data=broken?java.util.Arrays.copyOf(buffer.toByteArray(),12):buffer.toByteArray();
            String slug=(broken?"broken-":"spoofed-")+key;
            mvc.perform(multipart("/staff/products/create")
                    .file(new org.springframework.mock.web.MockMultipartFile("imageFiles","claimed.gif",broken?"image/png":"image/gif",data))
                    .with(user(new CustomUserDetails(staff))).with(csrf())
                    .param("name","Invalid image").param("slug",slug).param("categoryId",category.getId().toString())
                    .param("petType","DOG").param("status","ACTIVE"))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
            assertFalse(products.existsBySlug(slug));
        }
    }

    @Test void staffAndAdminProfileFormsUseRoutesAllowedForTheirRole() throws Exception {
        for (User actor : List.of(admin, staff)) {
            String html = mvc.perform(get("/account/profile").with(user(new CustomUserDetails(actor))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("action=\"/account/profile\""));
            assertTrue(html.contains("action=\"/account/password\""));
            assertTrue(html.contains("name=\"avatarFile\""));
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

    @Autowired StockImportItemRepository importItems;
    @Autowired ProductImageRepository productImages;
    @Autowired PaymentTransactionRepository paymentTransactions;

    private StockImportItem importLine(int quantity) {
        var receipt=imports.createStockImport(StockImportFormDto.builder().supplierName("Supplier")
                .items(List.of(StockImportItemDto.builder().variantId(variant.getId()).quantity(quantity).unitCost(BigDecimal.TEN).build())).build(),staff.getId());
        return importItems.findByStockImportId(receipt.getId()).get(0);
    }
    private com.petshop.dto.stock.StockImportEditFormDto editLine(StockImportItem line,int quantity) {
        return com.petshop.dto.stock.StockImportEditFormDto.builder().itemId(line.getId()).importId(line.getStockImport().getId())
                .quantity(quantity).unitCost(new BigDecimal("12.25")).build();
    }

    @Test void editedStockImportKeepsDeltaAndRejectsInvalidOrForeignInput() throws Exception {
        var line=importLine(3);var form=editLine(line,5);
        imports.updateImportItem(form,staff.getId());
        assertEquals(15,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(5,importItems.findById(line.getId()).orElseThrow().getQuantity());
        imports.updateImportItem(form,staff.getId());
        assertEquals(15,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertThrows(IllegalArgumentException.class,()->imports.updateImportItem(form,customer.getId()));
        form.setImportId(-1L);assertThrows(IllegalArgumentException.class,()->imports.updateImportItem(form,staff.getId()));
        form.setImportId(line.getStockImport().getId());form.setUnitCost(new BigDecimal("1.001"));
        assertThrows(IllegalArgumentException.class,()->imports.updateImportItem(form,staff.getId()));
        form.setUnitCost(BigDecimal.TEN);form.setQuantity(0);assertThrows(IllegalArgumentException.class,()->imports.updateImportItem(form,staff.getId()));
        form.setQuantity(null);assertThrows(IllegalArgumentException.class,()->imports.updateImportItem(form,staff.getId()));
        mvc.perform(get("/staff/stock-imports/items/"+line.getId()+"/edit").with(user(new CustomUserDetails(staff)))).andExpect(status().isOk());
        mvc.perform(post("/staff/stock-imports/items/"+line.getId()+"/edit").with(user(new CustomUserDetails(staff))).with(csrf())
                .param("importId",line.getStockImport().getId().toString()).param("quantity","").param("unitCost","12"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage","editForm"));
        mvc.perform(post("/staff/stock-imports/items/"+line.getId()+"/edit").with(user(new CustomUserDetails(customer))).with(csrf()))
                .andExpect(redirectedUrl("/error/403"));
        assertEquals(15,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test void concurrentReceiptEditsDoNotDoubleAdjustAndRejectInsufficientStock() throws Exception {
        var line=importLine(6);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var x=pool.submit(()->{gate.await();imports.updateImportItem(editLine(line,4),staff.getId());return true;});
            var y=pool.submit(()->{gate.await();imports.updateImportItem(editLine(line,4),admin.getId());return true;});
            gate.countDown();assertTrue(x.get(15,java.util.concurrent.TimeUnit.SECONDS));assertTrue(y.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(14,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
            new TransactionTemplate(transactionManager).executeWithoutResult(tx->assertEquals(1,variants.deductStockAtomic(variant.getId(),13)));
            assertThrows(IllegalArgumentException.class,()->imports.updateImportItem(editLine(line,1),staff.getId()));
            assertEquals(1,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
            assertEquals(4,importItems.findById(line.getId()).orElseThrow().getQuantity());
        } finally {pool.shutdownNow();}
    }

    @Test void productPrimaryImageSelectionIsOwnedAndPublicDetailsKeepFeedback() throws Exception {
        var first=productImages.save(ProductImage.builder().product(product).imageUrl("/images/first.png").isPrimary(true).sortOrder(0).build());
        var second=productImages.save(ProductImage.builder().product(product).imageUrl("/images/second.png").isPrimary(false).sortOrder(1).build());
        var form=ProductFormDto.fromEntity(productService.findById(product.getId()));
        assertEquals(first.getId(),form.getPrimaryImageId());form.setPrimaryImageId(second.getId());
        productService.updateProduct(product.getId(),form,staff.getId());
        assertFalse(productImages.findById(first.getId()).orElseThrow().getIsPrimary());
        assertTrue(productImages.findById(second.getId()).orElseThrow().getIsPrimary());
        assertEquals(second.getId(),ProductFormDto.fromEntity(productService.findById(product.getId())).getPrimaryImageId());
        form.setPrimaryImageId(Long.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->productService.updateProduct(product.getId(),form,staff.getId()));
        mvc.perform(get("/staff/products/"+product.getId()+"/edit").with(user(new CustomUserDetails(staff)))).andExpect(status().isOk());
        mvc.perform(get("/products/"+key)).andExpect(status().isOk()).andExpect(model().attributeExists("reviews","availableVariants"));
    }

    @Test void catalogSuggestionsAndOldFilterLinksKeepActiveProductRules() throws Exception {
        mvc.perform(get("/products/suggestions").param("keyword","x")).andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(0));
        mvc.perform(get("/products/suggestions").param("keyword",key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].id").value(category.getId()))
                .andExpect(jsonPath("$.products[0].id").value(product.getId()));
        var old=mvc.perform(get("/products").param("keyword",key).param("sort","priceAsc").param("priceRange","100to300"))
                .andExpect(status().isOk()).andReturn();
        var filter=(com.petshop.dto.catalog.ProductFilterDto)old.getModelAndView().getModel().get("filter");
        assertEquals("price_asc",filter.getSortBy());assertEquals(new BigDecimal("100000"),filter.getMinPrice());
        var descending=mvc.perform(get("/products").param("sort","priceDesc")).andExpect(status().isOk()).andReturn();
        assertEquals("price_desc",((com.petshop.dto.catalog.ProductFilterDto)descending.getModelAndView().getModel().get("filter")).getSortBy());
        var page=(org.springframework.data.domain.Page<?>)old.getModelAndView().getModel().get("productPage");assertEquals(1,page.getTotalElements());
        productService.deleteProduct(product.getId());
        mvc.perform(get("/products/suggestions").param("keyword",key)).andExpect(status().isOk()).andExpect(jsonPath("$.products.length()").value(0));
    }

    @Test void paidVnpayCannotBeRejectedAndKeepsBankReceiptAndAllocation() throws Exception {
        var order=purchasedOrder();
        orderService.confirmOrder(order.getId(),staff.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            var saved=orderRepository.findById(order.getId()).orElseThrow();saved.setPaymentMethod(PaymentMethod.VNPAY);
            saved.setPaymentStatus(PaymentStatus.PAID);saved.setOrderStatus(OrderStatus.CONFIRMED);orderRepository.save(saved);
            paymentTransactions.save(PaymentTransaction.builder().transactionCode("SUCCESS-"+key).order(saved).customer(customer)
                    .paymentMethod(PaymentMethod.VNPAY).amount(saved.getTotalAmount()).transactionStatus(TransactionStatus.SUCCESS)
                    .paidAt(java.time.LocalDateTime.now()).build());
        });
        mvc.perform(get("/staff/orders/"+order.getId()).with(user(new CustomUserDetails(staff)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/reject"))));
        assertThrows(IllegalStateException.class,()->orderService.customerCancelOrder(order.getId(),customer.getId(),"Cancel"));
        assertThrows(IllegalStateException.class,()->orderService.staffRejectOrder(order.getId(),staff.getId(),"Cannot deliver"));
        var saved=orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED,saved.getOrderStatus());assertEquals(PaymentStatus.PAID,saved.getPaymentStatus());
        assertTrue(saved.getStockDeducted());assertNull(saved.getCancelType());
        assertEquals(8,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(TransactionStatus.SUCCESS,paymentTransactions.findByOrderIdOrderByCreatedAtDesc(order.getId()).get(0).getTransactionStatus());
        assertThrows(IllegalStateException.class,()->orderService.staffRejectOrder(order.getId(),staff.getId(),"Again"));
        assertEquals(8,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }

    @Test void productAndReceiptPaginationRetainsEscapedKeywordAndAdminRoutes() throws Exception {
        product.setName("A&B " + key);products.save(product);
        products.save(Product.builder().category(category).createdBy(admin).name("A&B "+key+" extra").slug("extra-"+key).petType(PetType.DOG).build());
        productImages.save(ProductImage.builder().product(product).imageUrl("/images/visible.png").isPrimary(true).build());
        for(var role:List.of(staff,admin)) {
            String base=role==admin ? "/admin/products" : "/staff/products";
            String html=mvc.perform(get(base).param("size","1").param("keyword","A&B "+key).with(user(new CustomUserDetails(role))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("A%26B"));assertTrue(html.contains("/images/visible.png"));
            assertTrue(html.contains(base+"/create"));
            mvc.perform(get(base).param("size","1").param("page","1").param("keyword","A&B "+key).with(user(new CustomUserDetails(role))))
                    .andExpect(status().isOk());
        }
        importLine(2);importLine(2);
        mvc.perform(get("/staff/stock-imports").param("size","1").with(user(new CustomUserDetails(staff)))).andExpect(status().isOk());
    }

    @Autowired VoucherRepository journeyVouchers;
    @Autowired ReportService journeyReports;

    @Test void productPurchaseControlsMatchExistingRolesAndEmptyStockIsExplained() throws Exception {
        for(var operator:List.of(admin,staff)){
            String html=mvc.perform(get("/products/"+key).with(user(new CustomUserDetails(operator))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(html.contains("id=\"wishlistForm\""));assertFalse(html.contains("action=\"/cart/add\""));
        }
        String buyer=mvc.perform(get("/products/"+key).with(user(new CustomUserDetails(customer))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(buyer.contains("id=\"wishlistForm\""));assertTrue(buyer.contains("action=\"/cart/add\""));
        variant.setStockQuantity(0);variants.save(variant);
        String soldOut=mvc.perform(get("/products/"+key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(soldOut.contains("Hiện chưa có phân loại còn hàng"));assertFalse(soldOut.contains("action=\"/cart/add\""));
    }

    @Test void fullCodJourneyKeepsReceiptVoucherStockFeedbackAndReportsAligned() throws Exception {
        variant.setStockQuantity(0);variants.save(variant);
        importLine(10);
        var voucher=journeyVouchers.save(Voucher.builder().code("JOURNEY"+key.replace("-","").toUpperCase(java.util.Locale.ROOT))
                .title("Journey voucher").discountType(DiscountType.FIXED).discountValue(new BigDecimal("50000"))
                .startDate(java.time.LocalDateTime.now().minusDays(1)).endDate(java.time.LocalDateTime.now().plusDays(1))
                .totalUsageLimit(10).perCustomerLimit(1).build());
        var buyer=user(new CustomUserDetails(customer));
        var employee=user(new CustomUserDetails(staff));
        var manager=user(new CustomUserDetails(admin));
        mvc.perform(post("/cart/add").with(buyer).with(csrf()).param("variantId",variant.getId().toString()).param("quantity","2"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/checkout").with(buyer)).andExpect(status().isOk());
        String success=mvc.perform(post("/checkout/place-order").with(buyer).with(csrf())
                        .param("recipientName","Nguyễn Văn Demo").param("recipientPhone","0901234567")
                        .param("shippingProvince","Hồ Chí Minh").param("shippingDistrict","Thủ Đức")
                        .param("shippingWard","Linh Trung").param("shippingAddressLine","123 Đường số 1")
                        .param("paymentMethod","COD").param("voucherCode",voucher.getCode()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertNotNull(success);assertTrue(success.startsWith("/checkout/success/"));
        var order=orderService.getOrderByCodeForCustomer(success.substring(success.lastIndexOf('/')+1),customer.getId());
        assertEquals(0,new BigDecimal("380000").compareTo(order.getTotalAmount()));
        assertFalse(order.getStockDeducted());assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0,carts.getCartForUser(customer.getId()).getTotalQuantity());
        mvc.perform(get(success).with(buyer)).andExpect(status().isOk());
        mvc.perform(get("/customer/orders/"+order.getId()).with(buyer)).andExpect(status().isOk());
        var item=orderItems.findByOrderId(order.getId()).get(0);
        mvc.perform(post("/customer/reviews/create").with(buyer).with(csrf()).param("orderItemId",item.getId().toString())
                        .param("rating","5").param("comment","Sản phẩm đúng mô tả và giao hàng tốt"))
                .andExpect(flash().attributeExists("errorMessage"));
        for(String action:List.of("confirm","prepare","ship","complete")){
            mvc.perform(post("/staff/orders/"+order.getId()+"/"+action).with(employee).with(csrf()))
                    .andExpect(flash().attributeExists("successMessage"));
            assertEquals(8,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        }
        var completed=orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.COMPLETED,completed.getOrderStatus());assertEquals(PaymentStatus.PAID,completed.getPaymentStatus());
        assertNotNull(completed.getCompletedAt());
        mvc.perform(post("/staff/orders/"+order.getId()+"/complete").with(employee).with(csrf()))
                .andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/customer/reviews/create").with(buyer).with(csrf()).param("orderItemId",item.getId().toString())
                        .param("rating","5").param("comment","Sản phẩm đúng mô tả và giao hàng tốt"))
                .andExpect(flash().attributeExists("successMessage"));
        var review=reviewRepository.findAll().stream().filter(r->r.getOrderItem().getId().equals(item.getId())).findFirst().orElseThrow();
        mvc.perform(post("/admin/reviews/"+review.getId()+"/reply").with(manager).with(csrf()).param("replyText","Cảm ơn bạn đã mua hàng tại cửa hàng"))
                .andExpect(flash().attributeExists("successMessage"));
        assertTrue(mvc.perform(get("/products/"+key)).andReturn().getResponse().getContentAsString().contains("Cảm ơn bạn đã mua hàng"));
        mvc.perform(post("/admin/reviews/"+review.getId()+"/toggle-status").with(manager).with(csrf()))
                .andExpect(flash().attributeExists("successMessage"));
        assertFalse(mvc.perform(get("/products/"+key)).andReturn().getResponse().getContentAsString().contains("Cảm ơn bạn đã mua hàng"));
        assertTrue(mvc.perform(get("/customer/reviews").with(buyer)).andReturn().getResponse().getContentAsString().contains("Cảm ơn bạn đã mua hàng"));
        var now=java.time.LocalDate.now();
        Object[] sold=journeyReports.productSales(now.atStartOfDay(),now.plusDays(1).atStartOfDay(),PageRequest.of(0,100))
                .stream().filter(r->((Number)r[1]).longValue()==product.getId()).findFirst().orElseThrow();
        assertEquals(2L,((Number)sold[2]).longValue());assertEquals(0,new BigDecimal("400000").compareTo(new BigDecimal(sold[3].toString())));
        var stock=journeyReports.stockReconciliation().stream().filter(r->r.getSku().equals(variant.getSkuCode())).findFirst().orElseThrow();
        assertEquals(0,stock.getDifference());assertEquals(8,stock.getCurrentStock());
        assertEquals(1,journeyVouchers.findById(voucher.getId()).orElseThrow().getUsedCount());
    }

    @Test void receiptDecreaseAndConfirmationCannotBothConsumeSameAvailableStock() throws Exception {
        var line=importLine(6);carts.addItemToCart(customer.getId(),variant.getId(),12);
        var order=orderService.checkoutOrder(customer.getId(),com.petshop.dto.order.CheckoutSubmitDto.builder()
                .recipientName("Customer").recipientPhone("0901234567").shippingProvince("HCM").shippingDistrict("Thu Duc")
                .shippingWard("Linh Trung").shippingAddressLine("123 Test").paymentMethod(PaymentMethod.COD).build());
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var edit=pool.submit(()->{gate.await();try{imports.updateImportItem(editLine(line,1),staff.getId());return true;}catch(IllegalArgumentException e){return false;}});
            var confirmation=pool.submit(()->{gate.await();try{orderService.confirmOrder(order.getId(),staff.getId());return true;
                }catch(IllegalArgumentException|IllegalStateException e){return false;}});
            gate.countDown();boolean edited=edit.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(edited,confirmation.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(edited ? 11 : 4,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
            assertEquals(edited ? 1 : 6,importItems.findById(line.getId()).orElseThrow().getQuantity());
        } finally {pool.shutdownNow();}
    }
}
