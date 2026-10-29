package com.petshop.checkout;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.*;
import com.petshop.repository.*;
import com.petshop.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        // A dedicated context avoids MockMvc's csrf() helper replacing this filter's repository.
        "spring.datasource.url=jdbc:h2:mem:jwtcsrf;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionCartCsrfRegressionTests {
    @Autowired MockMvc mvc;
    @Autowired CartService carts;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;

    @Test
    void sessionStaticAndApiRequestsKeepCsrfAndRealHeaderStillRequired() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0,8);
        Role role = roles.findByCode("CUSTOMER").orElseGet(() -> roles.save(Role.builder().code("CUSTOMER").name("Customer").build()));
        User customer = users.save(User.builder().role(role).email(suffix+"@csrf.test").fullName("CSRF regression").passwordHash("unused").build());
        Category category = categories.save(Category.builder().code(suffix).name("Test").build());
        Product product = products.save(Product.builder().category(category).slug(suffix).name("Test product").createdBy(customer).build());
        ProductVariant variant = variants.save(ProductVariant.builder().product(product).skuCode(suffix).variantName("SKU")
                .price(new BigDecimal("100000")).stockQuantity(5).build());
        Long itemId = carts.addItemToCart(customer.getId(),variant.getId(),1).getItems().get(0).getId();
        var principal = new CustomUserDetails(customer);
        MvcResult page = mvc.perform(get("/cart").with(user(principal))).andExpect(status().isOk()).andReturn();
        var csrfSession = (org.springframework.mock.web.MockHttpSession) page.getRequest().getSession(false);
        assertThat(csrfSession).isNotNull();
        var matcher = Pattern.compile("data-csrf-token=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        String renderedToken = matcher.group(1);
        for (String path : new String[]{"/js/cart.js", "/cart/api"}) {
            mvc.perform(get(path).with(user(principal)).session(csrfSession)).andExpect(status().isOk());
        }
        mvc.perform(post("/cart/api/selection").with(user(principal)).session(csrfSession)
                .header("X-CSRF-TOKEN",renderedToken).param("itemId",itemId.toString()).param("selected","false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selectedItemCount").value(0));
        mvc.perform(post("/cart/api/selection").with(user(principal)).session(csrfSession)
                .param("itemId",itemId.toString()).param("selected","true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"));
        assertThat(carts.getCartForUser(customer.getId()).getSelectedItemCount()).isZero();
    }
}
