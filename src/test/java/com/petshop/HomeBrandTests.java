package com.petshop;

import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homeBrands;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomeBrandTests {
    @Autowired MockMvc mvc;
    @Autowired BrandRepository brands;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired CategoryRepository categories;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    String key;
    User author;
    Category category;

    @BeforeEach void fixture() {
        key = UUID.randomUUID().toString();
        Role role = roles.findByCode("STAFF").orElseGet(() -> roles.save(Role.builder().code("STAFF").name("Staff").build()));
        author = users.save(User.builder().role(role).fullName("Brand fixture").email(key + "@brands.test").passwordHash("unused").build());
        category = categories.save(Category.builder().name("Food").code(key).build());
    }

    Brand brand(String name, boolean active, String logo) {
        return brands.save(Brand.builder().name(name + key).isActive(active).logoUrl(logo).build());
    }

    Product product(Brand brand, ProductStatus status, ProductVariantStatus variantStatus) {
        Product product = products.save(Product.builder().name("Product " + key).slug(UUID.randomUUID().toString())
                .category(category).brand(brand).status(status).createdBy(author).build());
        if (variantStatus != null) variants.save(ProductVariant.builder().product(product).skuCode(UUID.randomUUID().toString())
                .variantName("Standard").price(new BigDecimal("50000")).stockQuantity(0).status(variantStatus).build());
        return product;
    }

    @Test void homepageOnlyLinksActiveBrandsWithPublishedSellableProducts() throws Exception {
        Brand visible = brand("Available ", true, "/uploads/brands/logo.png");
        Product first = product(visible, ProductStatus.ACTIVE, ProductVariantStatus.ACTIVE);
        variants.save(ProductVariant.builder().product(first).skuCode(UUID.randomUUID().toString())
                .variantName("Large").price(new BigDecimal("90000")).build());
        Brand textOnly = brand("Without logo ", true, null);
        product(textOnly, ProductStatus.ACTIVE, ProductVariantStatus.ACTIVE);
        Brand inactive = brand("Inactive ", false, null);
        product(inactive, ProductStatus.ACTIVE, ProductVariantStatus.ACTIVE);
        Brand discontinued = brand("Discontinued ", true, null);
        product(discontinued, ProductStatus.DISCONTINUED, ProductVariantStatus.ACTIVE);
        Brand noSku = brand("No SKU ", true, null);
        product(noSku, ProductStatus.ACTIVE, null);
        Brand inactiveSku = brand("Inactive SKU ", true, null);
        product(inactiveSku, ProductStatus.ACTIVE, ProductVariantStatus.INACTIVE);
        Brand unused = brand("Unused ", true, null);

        String html = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"homeBrands\"", "src=\"/uploads/brands/logo.png\"",
                "href=\"/products?brandId=" + visible.getId() + "\"", textOnly.getName());
        assertThat(html.split("data-brand-id=\"" + visible.getId() + "\"", -1)).hasSize(2);
        for (Brand hidden : java.util.List.of(inactive, discontinued, noSku, inactiveSku, unused)) {
            assertThat(html).doesNotContain("data-brand-id=\"" + hidden.getId() + "\"");
        }
        String filtered = mvc.perform(get("/products").param("brandId", textOnly.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(filtered).contains(textOnly.getName()).doesNotContain("href=\"/products/" + first.getSlug() + "\"");
    }

    @Test void brandNameSearchWorksWithCombinedFilterAndUnbrandedProducts() throws Exception {
        Brand target = brand("SearchOnly ", true, null);
        Product matchingBrand = product(target, ProductStatus.ACTIVE, ProductVariantStatus.ACTIVE);
        Product matchingName = product(null, ProductStatus.ACTIVE, ProductVariantStatus.ACTIVE);
        matchingName.setName(target.getName() + " special item");
        products.saveAndFlush(matchingName);
        String query = target.getName().toLowerCase(java.util.Locale.ROOT);
        String html = mvc.perform(get("/products").param("keyword", query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/products/" + matchingBrand.getSlug(), "/products/" + matchingName.getSlug());
        String filtered = mvc.perform(get("/products").param("keyword", query).param("brandId", target.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(filtered).contains("/products/" + matchingBrand.getSlug())
                .doesNotContain("/products/" + matchingName.getSlug());
        mvc.perform(get("/products/suggestions").param("keyword", query)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.products[*].id", org.hamcrest.Matchers.hasItem(matchingBrand.getId().intValue())));
    }
}
