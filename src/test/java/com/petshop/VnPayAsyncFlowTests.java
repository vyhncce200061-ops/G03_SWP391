package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.EmailService;
import com.petshop.service.VnPayService;
import com.petshop.service.VNPayReturnWaitService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.ModelAndView;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:asyncReturn;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VnPayAsyncFlowTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @MockBean VnPayService gateway;
    @MockBean VNPayReturnWaitService waiting;
    @MockBean EmailService mail;
    User customer;

    @BeforeEach void fixture() {
        Role role = roles.findByCode("CUSTOMER").orElseGet(() -> roles.save(Role.builder().code("CUSTOMER").name("Customer").build()));
        customer = users.save(User.builder().role(role).email(UUID.randomUUID() + "@async.test")
                .fullName("Async customer").passwordHash("unused").status("ACTIVE").build());
    }

    @Test void verifiedPendingReturnRendersSharedLayoutThroughAsyncDispatch() throws Exception {
        when(gateway.findOwnedOrderIdForReturn(anyMap(), eq(customer.getId()))).thenReturn(Optional.of(20L));
        DeferredResult<ModelAndView> result = new DeferredResult<>();
        when(waiting.waitForResult(20L, customer.getId(), "TXN-TEST")).thenReturn(result);
        var started = mvc.perform(get("/payment/vnpay-return").param("vnp_TxnRef", "TXN-TEST")
                .with(user(new CustomUserDetails(customer)))).andExpect(request().asyncStarted()).andReturn();
        result.setResult(new ModelAndView("customer/payment-pending").addObject("orderId", 20L));
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/customer/orders/20")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/ui.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TP. Cần Thơ")));
        verify(gateway, never()).processIpn(anyMap());
    }

    @Test void anonymousReturnRedirectsThroughAsyncDispatchWithoutReadingOrder() throws Exception {
        var started = mvc.perform(get("/payment/vnpay-return")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(gateway, waiting);
    }

    @Test void invalidReturnKeepsErrorFlashAndNeverStartsPolling() throws Exception {
        when(gateway.findOwnedOrderIdForReturn(anyMap(), eq(customer.getId()))).thenReturn(Optional.empty());
        var started = mvc.perform(get("/payment/vnpay-return").with(user(new CustomUserDetails(customer))))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(redirectedUrl("/customer/orders"))
                .andExpect(flash().attribute("errorMessage", "Không thể xác minh kết quả thanh toán."));
        verifyNoInteractions(waiting);
    }
}
