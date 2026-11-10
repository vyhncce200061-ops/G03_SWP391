package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.controller.customer.VnPayController;
import com.petshop.entity.User;
import com.petshop.service.VnPayService;
import com.petshop.service.VNPayReturnWaitService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VnPayControllerTests {
    VnPayService service = mock(VnPayService.class);
    VNPayReturnWaitService waitService = mock(VNPayReturnWaitService.class);
    VnPayController controller = new VnPayController(service, waitService);

    @Test
    void anonymousReturnRequiresLoginWithoutInspectingOrder() {
        DeferredResult<ModelAndView> result = controller.paymentReturn(new MockHttpServletRequest(), null,
                new RedirectAttributesModelMap());
        assertThat(((ModelAndView) result.getResult()).getViewName()).isEqualTo("redirect:/login");
        verifyNoInteractions(service);
    }

    @Test
    void ownerReturnReadsOnlyAndRedirectsToExistingDetail() {
        CustomUserDetails principal = new CustomUserDetails(User.builder().id(7L).status("ACTIVE").build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("vnp_TxnRef", "TXN-TEST-1");
        when(service.findOwnedOrderIdForReturn(anyMap(), eq(7L))).thenReturn(Optional.of(10L));
        DeferredResult<ModelAndView> expected = new DeferredResult<>();
        when(waitService.waitForResult(10L, 7L, "TXN-TEST-1")).thenReturn(expected);
        assertThat(controller.paymentReturn(request, principal,
                new RedirectAttributesModelMap())).isSameAs(expected);
        verify(service, never()).processIpn(anyMap());
        verify(waitService).waitForResult(10L, 7L, "TXN-TEST-1");
    }

    @Test
    void invalidOrNonOwnerReturnRevealsNoOrderIdentifier() {
        CustomUserDetails principal = new CustomUserDetails(User.builder().id(8L).status("ACTIVE").build());
        when(service.findOwnedOrderIdForReturn(anyMap(), eq(8L))).thenReturn(Optional.empty());
        DeferredResult<ModelAndView> result = controller.paymentReturn(new MockHttpServletRequest(), principal,
                new RedirectAttributesModelMap());
        assertThat(((ModelAndView) result.getResult()).getViewName()).isEqualTo("redirect:/customer/orders");
        verify(service, never()).processIpn(anyMap());
        verifyNoInteractions(waitService);
    }

    @Test
    void ipnReturnsRetryCodeWhenTransactionCannotCommit() {
        when(service.processIpn(anyMap())).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(controller.ipn(new MockHttpServletRequest())).isEqualTo(
                Map.of("RspCode", "99", "Message", "Unable to process callback"));
    }
}
