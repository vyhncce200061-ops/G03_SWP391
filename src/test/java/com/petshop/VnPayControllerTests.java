package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.controller.customer.VnPayController;
import com.petshop.entity.User;
import com.petshop.service.VnPayService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VnPayControllerTests {
    VnPayService service = mock(VnPayService.class);
    VnPayController controller = new VnPayController(service);

    @Test
    void anonymousReturnRequiresLoginWithoutInspectingOrder() {
        assertThat(controller.paymentReturn(new MockHttpServletRequest(), null,
                new RedirectAttributesModelMap())).isEqualTo("redirect:/login");
        verifyNoInteractions(service);
    }

    @Test
    void ownerReturnReadsOnlyAndRedirectsToExistingDetail() {
        CustomUserDetails principal = new CustomUserDetails(User.builder().id(7L).status("ACTIVE").build());
        when(service.findOwnedOrderIdForReturn(anyMap(), eq(7L))).thenReturn(Optional.of(10L));
        assertThat(controller.paymentReturn(new MockHttpServletRequest(), principal,
                new RedirectAttributesModelMap())).isEqualTo("redirect:/customer/orders/10");
        verify(service, never()).processIpn(anyMap());
    }

    @Test
    void invalidOrNonOwnerReturnRevealsNoOrderIdentifier() {
        CustomUserDetails principal = new CustomUserDetails(User.builder().id(8L).status("ACTIVE").build());
        when(service.findOwnedOrderIdForReturn(anyMap(), eq(8L))).thenReturn(Optional.empty());
        assertThat(controller.paymentReturn(new MockHttpServletRequest(), principal,
                new RedirectAttributesModelMap())).isEqualTo("redirect:/customer/orders");
        verify(service, never()).processIpn(anyMap());
    }

    @Test
    void ipnReturnsRetryCodeWhenTransactionCannotCommit() {
        when(service.processIpn(anyMap())).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(controller.ipn(new MockHttpServletRequest())).isEqualTo(
                Map.of("RspCode", "99", "Message", "Unable to process callback"));
    }
}
