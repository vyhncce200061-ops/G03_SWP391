package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:staffProductWorkflow;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffProductWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductImageRepository images;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean com.petshop.service.EmailService mail;

    User staff;
    Product product;
    ProductVariant variant;
    Brand brand;
    String sku;

    @BeforeEach
    void setup() {
        String key = UUID.randomUUID().toString();
        sku = "SKU-" + key;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Role role = roles.findByCode("STAFF").orElseGet(() -> roles.save(Role.builder().code("STAFF").name("Staff").build()));
            staff = users.save(User.builder().role(role).fullName("Staff").email(key + "@staff.test").passwordHash("unused").build());
            Category category = categories.save(Category.builder().code(key).name("Category").build());
            brand = brands.save(Brand.builder().name("Brand " + key).isActive(false).build());
            product = products.save(Product.builder().name("Product").slug(key).category(category).brand(brand).createdBy(staff).build());
            variant = variants.save(ProductVariant.builder().product(product).skuCode(sku).variantName("Small")
                    .price(new BigDecimal("19500")).stockQuantity(10).build());
        });
    }

    @Test
    void failedVariantCreationKeepsCreateActionAndEditableSkuThenCanBeResubmitted() throws Exception {
        String create = "/staff/products/" + product.getId() + "/variants";
        String name = "New SKU " + sku;
        String html = mvc.perform(post(create).with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("id", variant.getId().toString()).param("skuCode", name).param("variantName", "Large")
                        .param("price", "-1").param("lowStockThreshold", "5").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("action=\"" + create + "\"", "Giá bán phải lớn hơn hoặc bằng 0");
        assertThat(html).doesNotContain("readonly=\"readonly\"", "/variants/null/edit");
        mvc.perform(post(create).with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("skuCode", name).param("variantName", "Large").param("price", "19500")
                        .param("lowStockThreshold", "5").param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(create));
        assertThat(variants.findBySkuCode(name).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    void duplicateSkuKeepsCreateActionAndShowsError() throws Exception {
        String create = "/staff/products/" + product.getId() + "/variants";
        String html = mvc.perform(post(create).with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("skuCode", sku).param("variantName", "Large").param("price", "19500")
                        .param("lowStockThreshold", "5").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("action=\"" + create + "\"", "Mã SKU đã tồn tại.");
        assertThat(html).doesNotContain("readonly=\"readonly\"", "/variants/null/edit");
    }

    @Test
    void failedVariantEditUsesPathIdAndKeepsOriginalSkuAndStockOnResubmit() throws Exception {
        String edit = "/staff/products/variants/" + variant.getId() + "/edit";
        String html = mvc.perform(post(edit).with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("id", "-999").param("productId", product.getId().toString()).param("skuCode", sku)
                        .param("variantName", "Changed").param("price", "invalid").param("lowStockThreshold", "5").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("action=\"" + edit + "\"", "readonly=\"readonly\"", "is-invalid");
        assertThat(html).doesNotContain("/variants/-999/edit", "/variants/null/edit");
        mvc.perform(post(edit).with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("productId", product.getId().toString()).param("skuCode", sku).param("variantName", "Changed")
                        .param("price", "19500").param("lowStockThreshold", "5").param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection());
        ProductVariant saved = variants.findById(variant.getId()).orElseThrow();
        assertThat(saved.getSkuCode()).isEqualTo(sku);
        assertThat(saved.getVariantName()).isEqualTo("Changed");
        assertThat(saved.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void productEditKeepsInactiveBrandAndImagesAfterValidationThenSavesNormally() throws Exception {
        ProductImage image = images.saveAndFlush(ProductImage.builder().product(product).imageUrl("/images/existing-product.png").isPrimary(true).build());
        for (String base : new String[]{"/staff/products", "/admin/products"}) {
            User actor = staff;
            if (base.startsWith("/admin")) {
                Role role = roles.findByCode("ADMIN").orElseGet(() -> roles.save(Role.builder().code("ADMIN").name("Admin").build()));
                actor = users.saveAndFlush(User.builder().role(role).fullName("Admin").email(UUID.randomUUID() + "@staff.test").passwordHash("unused").build());
            }
            String path = base + "/" + product.getId() + "/edit";
            String original = mvc.perform(get(path).with(user(new CustomUserDetails(actor))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(original).contains("value=\"" + brand.getId() + "\" selected=\"selected\"");
            String invalid = mvc.perform(multipart(path).with(user(new CustomUserDetails(actor))).with(csrf())
                            .param("name", "").param("slug", product.getSlug()).param("categoryId", product.getCategory().getId().toString())
                            .param("brandId", brand.getId().toString()).param("petType", "DOG").param("status", "ACTIVE")
                            .param("primaryImageId", image.getId().toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(invalid).contains("/images/existing-product.png", "value=\"" + brand.getId() + "\" selected=\"selected\"");
            mvc.perform(multipart(path).with(user(new CustomUserDetails(actor))).with(csrf())
                            .param("name", "Updated product").param("slug", product.getSlug()).param("categoryId", product.getCategory().getId().toString())
                            .param("brandId", brand.getId().toString()).param("petType", "DOG").param("status", "ACTIVE")
                            .param("primaryImageId", image.getId().toString()))
                    .andExpect(status().is3xxRedirection());
            new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                    assertThat(products.findById(product.getId()).orElseThrow().getBrand().getId()).isEqualTo(brand.getId()));
        }
    }

    @Test
    void missingBrandDoesNotSilentlyClearProductAssignment() throws Exception {
        String path = "/staff/products/" + product.getId() + "/edit";
        mvc.perform(multipart(path).with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("name", "Updated product").param("slug", product.getSlug()).param("categoryId", product.getCategory().getId().toString())
                        .param("brandId", "2147483647").param("petType", "DOG").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Không tìm thấy thương hiệu đã chọn.")));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                assertThat(products.findById(product.getId()).orElseThrow().getBrand().getId()).isEqualTo(brand.getId()));
    }

    @Test
    void rejectedUploadBatchRollsBackProductAndRemovesItsEarlierFile() throws Exception {
        java.nio.file.Path directory = java.nio.file.Path.of("uploads/products");
        java.nio.file.Files.createDirectories(directory);
        java.util.Set<java.nio.file.Path> before;
        try (var files = java.nio.file.Files.list(directory)) { before = files.collect(java.util.stream.Collectors.toSet()); }
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        String slug = "batch-" + UUID.randomUUID();
        mvc.perform(multipart("/staff/products/create")
                        .file(new org.springframework.mock.web.MockMultipartFile("imageFiles", "first.png", "image/png", buffer.toByteArray()))
                        .file(new org.springframework.mock.web.MockMultipartFile("imageFiles", "broken.png", "image/png", "invalid-image".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .with(user(new CustomUserDetails(staff))).with(csrf())
                        .param("name", "Rejected image batch").param("slug", slug).param("categoryId", product.getCategory().getId().toString())
                        .param("petType", "DOG").param("status", "ACTIVE"))
                .andExpect(status().isOk());
        assertThat(products.findBySlug(slug)).isEmpty();
        try (var files = java.nio.file.Files.list(directory)) {
            assertThat(files.collect(java.util.stream.Collectors.toSet())).isEqualTo(before);
        }
    }
}
