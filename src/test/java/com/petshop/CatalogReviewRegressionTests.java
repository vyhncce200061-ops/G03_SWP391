package com.petshop;

import com.petshop.dto.catalog.CategoryFormDto;
import com.petshop.entity.Category;
import com.petshop.entity.Product;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.CategoryRepository;
import com.petshop.repository.ProductRepository;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.CategoryManageService;
import com.petshop.service.EmailService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogReview;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogReviewRegressionTests {
    @MockBean EmailService mail;
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired CategoryManageService categoryService;
    @Autowired ProductRepository products;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;

    private Category category(String name, Category parent) {
        return categories.saveAndFlush(Category.builder().code(UUID.randomUUID().toString())
                .name(name).parentCategory(parent).build());
    }

    private CategoryFormDto moving(Category category, Integer parentId) {
        CategoryFormDto form = new CategoryFormDto();
        form.setCode(category.getCode());
        form.setName(category.getName());
        form.setParentId(parentId);
        return form;
    }

    @Test
    void suggestionsDoNotRepeatProductsWhenParentAndChildBothMatch() throws Exception {
        String key = UUID.randomUUID().toString();
        Category root = category("Food " + key, null);
        Category child = category("Food child " + key, root);
        Role role = roles.findByCode("CUSTOMER").orElseGet(() -> roles.save(Role.builder()
                .code("CUSTOMER").name("Customer").build()));
        User author = users.save(User.builder().role(role).fullName("Catalog fixture")
                .email(key + "@catalog.test").passwordHash("unused-test-hash").build());
        Product product = products.save(Product.builder().category(child).createdBy(author)
                .name("Food product " + key).slug(key).build());

        mvc.perform(get("/products/suggestions").param("keyword", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].id").value(product.getId()));
    }

    @Test
    void cachedRootCannotBeReparentedByAnotherTransaction() {
        Category root = category("Original root", null);
        Category newRoot = category("Destination root", null);
        Category child = category("New child", null);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(IllegalArgumentException.class, () -> outer.executeWithoutResult(status -> {
            Category cached = categories.findById(root.getId()).orElseThrow();
            assertNull(cached.getParentCategory());
            independent.executeWithoutResult(other -> categoryService.save(root.getId(), moving(root, newRoot.getId())));
            // The original persistence context still holds the old root level.
            categoryService.save(child.getId(), moving(child, root.getId()));
        }));

        assertNull(categoryService.editForm(root.getId()).getParentId());
        assertNull(categoryService.editForm(child.getId()).getParentId());
    }

    @Test
    void categoryShortcutEncodesTheWholeCodeAsOneFilterValue() throws Exception {
        mvc.perform(get("/categories/{code}", "Food&brandId=42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products?categoryCode=Food%26brandId%3D42"));
        mvc.perform(get("/categories/{code}", "Food+Cat"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products?categoryCode=Food%2BCat"));
    }

    @Test
    void staleStatusUpdateKeepsTheCommittedParentMove() {
        Category originalRoot = category("Original root", null);
        Category moved = category("Moved category", originalRoot);
        Category newRoot = category("New root", null);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            assertEquals(originalRoot.getId(), categories.findById(moved.getId()).orElseThrow().getParentCategory().getId());
            independent.executeWithoutResult(other -> categoryService.save(moved.getId(), moving(moved, newRoot.getId())));
            categoryService.toggleStatus(moved.getId());
        });

        CategoryFormDto result = categoryService.editForm(moved.getId());
        assertEquals(newRoot.getId(), result.getParentId());
        assertFalse(result.getIsActive());
    }

    @Test
    void simultaneousOppositeRootMovesAreBothRejected() throws Exception {
        Category first = category("First root", null);
        Category second = category("Second root", null);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var firstMove = pool.submit(() -> moveAfter(start, first, second));
            var secondMove = pool.submit(() -> moveAfter(start, second, first));
            start.countDown();
            int successful = (firstMove.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (secondMove.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(0, successful);
            Integer firstParent = categoryService.editForm(first.getId()).getParentId();
            Integer secondParent = categoryService.editForm(second.getId()).getParentId();
            assertNull(firstParent);
            assertNull(secondParent);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean moveAfter(CountDownLatch start, Category category, Category parent) throws Exception {
        start.await();
        try {
            categoryService.save(category.getId(), moving(category, parent.getId()));
            return true;
        } catch (IllegalArgumentException invalidTree) {
            return false;
        }
    }
}
