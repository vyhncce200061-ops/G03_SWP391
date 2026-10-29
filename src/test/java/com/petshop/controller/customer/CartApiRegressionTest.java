package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.cart.CartDto;
import com.petshop.dto.cart.CartItemDto;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartApiRegressionTest {
    @Autowired MockMvc mvc;
    @MockBean CartService service;
    @MockBean com.petshop.repository.UserRepository accountRepository;
    CustomUserDetails customer;
    CartDto cart;

    @BeforeEach
    void setUp() {
        User account = User.builder().id(1L).email("cart@example.test")
                .passwordHash("unused").fullName("Cart Customer").status("ACTIVE")
                .role(Role.builder().code("CUSTOMER").build()).build();
        customer = new CustomUserDetails(account);
        when(accountRepository.findById(1L)).thenReturn(java.util.Optional.of(account));
        cart = CartDto.builder().id(10L).userId(1L)
                .items(List.of(CartItemDto.builder().id(40L).quantity(2).isSelected(true)
                        .unitPrice(new BigDecimal("15000")).lineTotal(new BigDecimal("30000"))
                        .available(true).build()))
                .totalQuantity(2).selectedQuantity(2).selectedItemCount(1).checkoutAllowed(true)
                .subtotal(new BigDecimal("30000")).shippingFee(new BigDecimal("30000"))
                .totalAmount(new BigDecimal("60000")).build();
        when(service.getCartForUser(1L)).thenReturn(cart);
    }

    @Test
    void getReturnsCurrentCustomerCartWithStableSelectionJsonName() throws Exception {
        mvc.perform(get("/cart/api").with(user(customer)).param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.items[0].isSelected").value(true))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.checkoutAllowed").value(true))
                .andExpect(jsonPath("$.totalAmount").value(60000));
        verify(service).getCartForUser(1L);
        verify(service, never()).getCartForUser(999L);
    }

    @Test
    void quantityUpdateUsesAuthenticatedAccountAndReturnsServerState() throws Exception {
        when(service.updateItemQuantity(1L, 40L, 2)).thenReturn(cart);
        mvc.perform(post("/cart/api/quantity").with(user(customer)).with(csrf())
                        .param("itemId", "40").param("quantity", "2").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        verify(service).updateItemQuantity(1L, 40L, 2);
    }

    @Test
    void selectionUpdatePersistsFalseWithoutRequiringQuantityButton() throws Exception {
        when(service.updateItemSelection(1L, 40L, false)).thenReturn(cart);
        mvc.perform(post("/cart/api/selection").with(user(customer)).with(csrf())
                        .param("itemId", "40").param("selected", "false"))
                .andExpect(status().isOk());
        verify(service).updateItemSelection(1L, 40L, false);
    }

    @Test
    void invalidQuantityReturnsMessageAndUnmodifiedServerCart() throws Exception {
        when(service.updateItemQuantity(1L, 40L, 0)).thenThrow(new IllegalArgumentException("Số lượng phải lớn hơn 0."));
        mvc.perform(post("/cart/api/quantity").with(user(customer)).with(csrf())
                        .param("itemId", "40").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số lượng phải lớn hơn 0."))
                .andExpect(jsonPath("$.cart.items[0].quantity").value(2));
    }

    @Test
    void malformedAndOverflowingQuantitiesReturnUsableCartState() throws Exception {
        for (String invalid : List.of("abc", "1.5", "2147483648")) {
            mvc.perform(post("/cart/api/quantity").with(user(customer)).with(csrf())
                            .param("itemId", "40").param("quantity", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isString())
                    .andExpect(jsonPath("$.cart.items[0].quantity").value(2));
        }
        verify(service, never()).updateItemQuantity(any(), any(), anyInt());
    }

    @Test
    void missingParametersReturnUsableCartState() throws Exception {
        mvc.perform(post("/cart/api/selection").with(user(customer)).with(csrf())
                        .param("itemId", "40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.cart.userId").value(1));
    }

    @Test
    void mutationWithoutCsrfIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/cart/api/quantity").with(user(customer))
                        .param("itemId", "40").param("quantity", "2"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(302, 403));
        verify(service, never()).updateItemQuantity(any(), any(), anyInt());
    }

    @Test
    void unauthenticatedCartApiIsProtected() throws Exception {
        mvc.perform(get("/cart/api")).andExpect(status().is3xxRedirection());
        verify(service, never()).getCartForUser(any());
    }
}
