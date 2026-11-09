package com.petshop;

import com.petshop.dto.catalog.CategoryFormDto;
import com.petshop.dto.catalog.CategoryViewDto;
import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.EmailService;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogHierarchy;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogHierarchyTests {
    @MockBean EmailService mail;
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    String key, keyword;
    Category root, otherRoot, child, sibling, inactiveChild;
    User author;
    Product directProduct, childProduct, siblingProduct;
    Brand brand;

    @BeforeEach void fixture() {
        key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        keyword = "Hierarchy " + key;
        root = category("ROOT", "Thức ăn " + key, null, 10, true);
        otherRoot = category("OTHER", "Phụ kiện " + key, null, 20, true);
        child = category("CHILD", "Thức ăn hạt " + key, root, 200, true);
        sibling = category("SIBLING", "Pate " + key, root, 0, true);
        inactiveChild = category("INACTIVE", "Danh mục tạm ngừng " + key, root, 1, false);
        var role = roles.findByCode("CUSTOMER").orElseGet(() -> roles.save(Role.builder().code("CUSTOMER").name("Customer").build()));
        author = users.save(User.builder().role(role).fullName("Catalog customer").email(key + "@hierarchy.test").passwordHash("unused-hash").build());
        brand = brands.save(Brand.builder().name("Brand " + key).logoUrl("/images/old-brand.png").isActive(false).build());
        directProduct = product(root, "direct");
        childProduct = product(child, "child");
        siblingProduct = product(sibling, "sibling");
    }

    Category category(String code, String name, Category parent, int order, boolean active) {
        return categories.saveAndFlush(Category.builder().code(code + key).name(name).parentCategory(parent).sortOrder(order).isActive(active).build());
    }
    Product product(Category category, String suffix) {
        Product product = products.saveAndFlush(Product.builder().name(keyword + " " + suffix).slug(key + "-" + suffix)
                .category(category).createdBy(author).petType(PetType.DOG).build());
        variants.saveAndFlush(ProductVariant.builder().product(product).skuCode(key + suffix).variantName("Standard")
                .price(new BigDecimal("125000")).stockQuantity(10).build());
        return product;
    }
    MockHttpServletRequestBuilder categoryPost(String path, String code, String name, String parentId) {
        return post(path).with(user("admin").roles("ADMIN")).with(csrf()).param("code", code).param("name", name)
                .param("parentId", parentId).param("sortOrder", "0").param("isActive", "true");
    }
    MvcResult adminGet(String path) throws Exception {
        return mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
    }
    @SuppressWarnings("unchecked") <T> List<T> list(MvcResult result, String name) {
        return (List<T>) result.getModelAndView().getModel().get(name);
    }
    @SuppressWarnings("unchecked") Set<Long> ids(MvcResult result, String pageName) {
        return ((Page<?>) result.getModelAndView().getModel().get(pageName)).stream().map(p -> p instanceof Product
                ? ((Product) p).getId() : ((ProductSummaryDto) p).getId()).collect(java.util.stream.Collectors.toSet());
    }
    String parentSelect(MvcResult result) throws Exception {
        String html = result.getResponse().getContentAsString();
        var m = Pattern.compile("(?s)<select[^>]*name=\"parentId\"[^>]*>.*?</select>").matcher(html);
        assertTrue(m.find()); return m.group();
    }

    @Test void homepageShowsMinimumActiveSkuPriceAndOnlyParentCategoryCards() throws Exception {
        childProduct.setIsFeatured(true); products.saveAndFlush(childProduct);
        variants.saveAndFlush(ProductVariant.builder().product(childProduct).skuCode(key + "max").variantName("Large")
                .price(new BigDecimal("990000")).stockQuantity(5).build());
        variants.saveAndFlush(ProductVariant.builder().product(childProduct).skuCode(key + "inactive").variantName("Inactive")
                .price(BigDecimal.ONE).stockQuantity(5).status(ProductVariantStatus.INACTIVE).build());
        var result = mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        List<CategoryViewDto> tree = list(result, "categories");
        var branch = tree.stream().filter(c -> c.getId().equals(root.getId())).findFirst().orElseThrow();
        assertThat(branch.getChildren()).extracting(CategoryViewDto::getId).containsExactly(sibling.getId(), child.getId());
        String html = result.getResponse().getContentAsString();
        var m = Pattern.compile("(?s)data-product-id=\"" + childProduct.getId() + "\".*?class=\"[^\"]*featured-price[^\"]*\"[^>]*>(.*?)</span>").matcher(html);
        assertTrue(m.find()); assertEquals("125.000 đ", m.group(1).strip());
        assertThat(html).contains(root.getName(), otherRoot.getName(), "class=\"category-cover\"")
                .doesNotContain(child.getName(), sibling.getName(), inactiveChild.getName(), "Danh mục cha", "Danh mục con");
    }

    @Test void categoryListKeepsChildrenUnderTheirParentAndDisplaysTheRelationship() throws Exception {
        var result = adminGet("/admin/categories");
        List<CategoryViewDto> rows = list(result, "categories");
        int index = java.util.stream.IntStream.range(0, rows.size()).filter(i -> rows.get(i).getId().equals(root.getId())).findFirst().orElseThrow();
        assertThat(rows.subList(index + 1, index + 4)).extracting(CategoryViewDto::getId)
                .containsExactly(sibling.getId(), inactiveChild.getId(), child.getId());
        assertEquals(root.getId(), rows.get(index + 3).getParentId());
        assertThat(result.getResponse().getContentAsString()).contains("Danh mục cha", "Danh mục con", "data-parent-id=\"" + root.getId() + "\"");
    }

    @Test void createFormLoadsRootsAndEditFormSelectsItsExistingParent() throws Exception {
        var create = adminGet("/admin/categories/create");
        List<CategoryViewDto> options = list(create, "parentCategories");
        assertThat(options).extracting(CategoryViewDto::getId).contains(root.getId(), otherRoot.getId()).doesNotContain(child.getId(), sibling.getId());
        assertNull(((CategoryFormDto) create.getModelAndView().getModel().get("categoryForm")).getParentId());
        var edit = adminGet("/admin/categories/edit/" + child.getId());
        assertEquals(root.getId(), ((CategoryFormDto) edit.getModelAndView().getModel().get("categoryForm")).getParentId());
        assertThat(parentSelect(edit)).containsPattern("<option[^>]*value=\"" + root.getId() + "\"[^>]*selected=\"selected\"");
        var parent = adminGet("/admin/categories/edit/" + root.getId());
        assertEquals(true, parent.getModelAndView().getModel().get("parentLocked"));
        assertThat(parentSelect(parent)).contains("disabled=\"disabled\"").doesNotContain("value=\"" + root.getId() + "\"");
    }

    @Test void validCreateMoveAndPromotePreserveTheProductAssignment() throws Exception {
        String code = "NEW" + key;
        mvc.perform(categoryPost("/admin/categories/create", "  " + code + "  ", "  New child  ", root.getId().toString())
                .param("id", otherRoot.getId().toString())).andExpect(redirectedUrl("/admin/categories"));
        Category created = categories.findByCode(code).orElseThrow();
        assertEquals(root.getId(), created.getParentCategory().getId()); assertEquals("New child", created.getName());
        assertNotEquals(otherRoot.getId(), created.getId());
        mvc.perform(categoryPost("/admin/categories/update/" + child.getId(), child.getCode(), child.getName(), otherRoot.getId().toString()))
                .andExpect(redirectedUrl("/admin/categories"));
        assertEquals(otherRoot.getId(), categories.findById(child.getId()).orElseThrow().getParentCategory().getId());
        mvc.perform(categoryPost("/admin/categories/update/" + child.getId(), child.getCode(), child.getName(), ""))
                .andExpect(redirectedUrl("/admin/categories"));
        assertNull(categories.findById(child.getId()).orElseThrow().getParentCategory());
        assertEquals(child.getId(), products.findById(childProduct.getId()).orElseThrow().getCategory().getId());
    }

    @ParameterizedTest @ValueSource(strings = {"self", "child", "missing", "root-with-children"})
    void invalidHierarchyDoesNotChangeExistingRelationships(String situation) throws Exception {
        Category edited = situation.equals("root-with-children") ? root : child;
        String parent = switch (situation) {
            case "self" -> child.getId().toString();
            case "child" -> sibling.getId().toString();
            case "missing" -> "2147483647";
            default -> otherRoot.getId().toString();
        };
        mvc.perform(categoryPost("/admin/categories/update/" + edited.getId(), edited.getCode(), edited.getName(), parent))
                .andExpect(status().isOk()).andExpect(view().name("admin/category-form")).andExpect(model().attributeHasErrors("categoryForm"));
        Category saved = categories.findById(edited.getId()).orElseThrow();
        if (edited.getId().equals(root.getId())) assertNull(saved.getParentCategory());
        else assertEquals(root.getId(), saved.getParentCategory().getId());
    }

    @Test void invalidFieldsAndDuplicateCodeRetainTheSelectedParentAndEnteredText() throws Exception {
        long count = categories.count();
        var invalid = mvc.perform(categoryPost("/admin/categories/create", "  " + root.getCode().toLowerCase(Locale.ROOT) + "  ", "New A&B", otherRoot.getId().toString()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("categoryForm")).andReturn();
        assertThat(parentSelect(invalid)).containsPattern("<option[^>]*value=\"" + otherRoot.getId() + "\"[^>]*selected=\"selected\"");
        assertThat(invalid.getResponse().getContentAsString()).contains("New A&amp;B");
        for (String[] bad : List.of(new String[]{"code", " "}, new String[]{"name", "x".repeat(151)},
                new String[]{"description", "x".repeat(1001)}, new String[]{"sortOrder", "-1"},
                new String[]{"sortOrder", "2147483648"}, new String[]{"parentId", "not-an-id"})) {
            var parameters = new org.springframework.util.LinkedMultiValueMap<String, String>();
            parameters.set("code", "VALID" + key); parameters.set("name", "Valid");
            parameters.set("parentId", root.getId().toString()); parameters.set("sortOrder", "0");
            parameters.set(bad[0], bad[1]);
            mvc.perform(post("/admin/categories/create").with(user("admin").roles("ADMIN")).with(csrf()).params(parameters)).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("categoryForm", bad[0]));
        }
        assertEquals(count, categories.count());
    }

    @ParameterizedTest @CsvSource({"/products,productPage,CUSTOMER", "/admin/products,products,ADMIN", "/staff/products,products,STAFF"})
    void parentFilterIncludesDirectAndChildProductsWhileChildFilterIsSpecific(String path, String pageName, String role) throws Exception {
        var parentResult = mvc.perform(get(path).with(user("operator").roles(role)).param("keyword", keyword)
                .param("categoryId", root.getId().toString())).andExpect(status().isOk()).andReturn();
        assertThat(ids(parentResult, pageName)).containsExactlyInAnyOrder(directProduct.getId(), childProduct.getId(), siblingProduct.getId());
        var childResult = mvc.perform(get(path).with(user("operator").roles(role)).param("keyword", keyword)
                .param("categoryId", child.getId().toString())).andExpect(status().isOk()).andReturn();
        assertThat(ids(childResult, pageName)).containsExactly(childProduct.getId());
        assertThat(childResult.getResponse().getContentAsString()).contains(root.getName(), child.getName());
    }

    @Test void inactiveBranchesDisappearFromMenusWithoutChangingProductSaleStatus() throws Exception {
        mvc.perform(post("/admin/categories/toggle-status/" + root.getId()).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/categories"));
        var home = mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        assertThat(home.getResponse().getContentAsString()).doesNotContain(root.getName(), child.getName(), sibling.getName());
        assertTrue(categories.findById(child.getId()).orElseThrow().getIsActive());
        assertFalse(categories.findById(inactiveChild.getId()).orElseThrow().getIsActive());
        assertEquals(ProductStatus.ACTIVE, products.findById(childProduct.getId()).orElseThrow().getStatus());
        var result = mvc.perform(get("/products").param("keyword", keyword)).andExpect(status().isOk()).andReturn();
        assertThat(ids(result, "productPage")).contains(childProduct.getId());
        mvc.perform(post("/admin/categories/toggle-status/" + root.getId()).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/categories"));
        String restored = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(restored).contains(root.getName()).doesNotContain(child.getName(), inactiveChild.getName());
        String catalog = mvc.perform(get("/products")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(catalog).contains(child.getName()).doesNotContain(inactiveChild.getName());
    }

    @ParameterizedTest @ValueSource(strings = {"/admin/products/", "/staff/products/"})
    void productFormShowsFullCategoryPathAndRetainsAnInactiveExistingAssignment(String path) throws Exception {
        child.setIsActive(false); categories.saveAndFlush(child);
        var result = mvc.perform(get(path + childProduct.getId() + "/edit").with(user("operator").roles(path.contains("admin") ? "ADMIN" : "STAFF")))
                .andExpect(status().isOk()).andReturn();
        List<CategoryViewDto> options = list(result, "categories");
        assertThat(options).extracting(CategoryViewDto::getId).contains(child.getId()).doesNotContain(inactiveChild.getId());
        assertThat(result.getResponse().getContentAsString()).contains(root.getName() + " / " + child.getName() + " (ngừng hiển thị)");
    }

    @Test void brandFormLivesInOneModalAndDoesNotContainLogoControls() throws Exception {
        String html = adminGet("/admin/brands").getResponse().getContentAsString();
        assertThat(html).contains("id=\"brandModal\"", "data-brand-action=\"/admin/brands/create\"", "/js/admin-brand.js")
                .doesNotContain("logoUrl", "Đường dẫn logo", "Chưa có logo");
        assertThat(html.split("id=\"brandForm\"", -1)).hasSize(2);
        assertThat(html.indexOf("id=\"brandForm\"")).isGreaterThan(html.indexOf("id=\"brandModal\""));
    }

    @Test void brandCreationTrimsFieldsAndIgnoresForgedEntityProperties() throws Exception {
        mvc.perform(post("/admin/brands/create").with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", "  New brand " + key + "  ").param("description", "  Description  ")
                .param("id", brand.getId().toString()).param("isActive", "false").param("logoUrl", "untrusted"))
                .andExpect(redirectedUrl("/admin/brands"));
        Brand saved = brands.findAll().stream().filter(b -> b.getName().equals("New brand " + key)).findFirst().orElseThrow();
        assertNotEquals(brand.getId(), saved.getId()); assertTrue(saved.getIsActive()); assertNull(saved.getLogoUrl());
        assertEquals("Description", saved.getDescription());
    }

    @Test void brandUpdateKeepsLegacyLogoAndStatusAndCanKeepItsOwnName() throws Exception {
        mvc.perform(post("/admin/brands/update/" + brand.getId()).with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", brand.getName()).param("description", "Updated").param("logoUrl", "untrusted").param("isActive", "true"))
                .andExpect(redirectedUrl("/admin/brands"));
        Brand saved = brands.findById(brand.getId()).orElseThrow();
        assertEquals("/images/old-brand.png", saved.getLogoUrl()); assertFalse(saved.getIsActive()); assertEquals("Updated", saved.getDescription());
    }

    @ParameterizedTest @ValueSource(strings = {"blank", "duplicate", "long-name", "long-description"})
    void invalidBrandReopensItsModalWithoutCreatingData(String situation) throws Exception {
        long before = brands.count();
        String name = switch (situation) { case "blank" -> "   "; case "duplicate" -> "  " + brand.getName().toLowerCase(Locale.ROOT) + "  "; case "long-name" -> "x".repeat(151); default -> "New " + key; };
        var result = mvc.perform(post("/admin/brands/create").with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", name).param("description", situation.equals("long-description") ? "x".repeat(1001) : "Retained A&B"))
                .andExpect(status().isOk()).andExpect(model().attribute("brandModalOpen", true)).andExpect(model().attributeHasErrors("brandForm")).andReturn();
        assertEquals(before, brands.count());
        assertThat(result.getResponse().getContentAsString()).contains("data-open=\"true\"");
        if (!situation.equals("long-description")) assertThat(result.getResponse().getContentAsString()).contains("Retained A&amp;B");
    }

    @Test void duplicateBrandUpdateKeepsTheEditActionAndDoesNotModifyEitherRecord() throws Exception {
        Brand other = brands.saveAndFlush(Brand.builder().name("Other brand " + key).build());
        var result = mvc.perform(post("/admin/brands/update/" + brand.getId()).with(user("admin").roles("ADMIN")).with(csrf())
                .param("name", other.getName().toUpperCase(Locale.ROOT)).param("description", "Retain this"))
                .andExpect(status().isOk()).andExpect(model().attribute("editingBrandId", brand.getId()))
                .andExpect(model().attributeHasFieldErrors("brandForm", "name")).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("action=\"/admin/brands/update/" + brand.getId() + "\"");
        assertEquals(brand.getName(), brands.findById(brand.getId()).orElseThrow().getName());
    }

    @Test void brandAndCategoryWritesStillRequireAdminAndCsrf() throws Exception {
        mvc.perform(post("/admin/brands/create").with(user("staff").roles("STAFF")).with(csrf()).param("name", "Forbidden"))
                .andExpect(redirectedUrl("/error/403"));
        mvc.perform(post("/admin/categories/create").with(user("admin").roles("ADMIN")).param("code", "Forbidden").param("name", "Forbidden"))
                .andExpect(redirectedUrl("/error/403"));
    }
}
