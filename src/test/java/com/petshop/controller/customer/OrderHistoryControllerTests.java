package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.Order;
import com.petshop.entity.OrderItem;
import com.petshop.entity.OrderStatusHistory;
import com.petshop.entity.User;
import com.petshop.entity.enums.OrderStatus;
import com.petshop.entity.enums.PaymentMethod;
import com.petshop.entity.enums.PaymentStatus;
import com.petshop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderHistoryControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private com.petshop.repository.UserRepository accountRepository;

    private CustomUserDetails customer;

    @BeforeEach
    void prepare() {
        User account = User.builder().id(7L).email("customer@example.test")
                .passwordHash("unused").fullName("Khách hàng").status("ACTIVE")
                .role(com.petshop.entity.Role.builder().code("CUSTOMER").build()).build();
        customer = new CustomUserDetails(account);
        when(accountRepository.findById(7L)).thenReturn(java.util.Optional.of(account));
        when(orderService.getCustomerOrders(eq(7L), nullable(OrderStatus.class), nullable(LocalDate.class),
                nullable(LocalDate.class), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(4, Pageable.class)));
    }

    @Test
    void filtersAreForwardedWithAuthenticatedOwnerAndPagination() throws Exception {
        Page<Order> orders = new PageImpl<>(List.of(order()), PageRequest.of(1, 1), 3);
        when(orderService.getCustomerOrders(eq(7L), eq(OrderStatus.CONFIRMED),
                eq(LocalDate.of(2026, 10, 18)), eq(LocalDate.of(2026, 10, 18)), any(Pageable.class)))
                .thenReturn(orders);

        mvc.perform(get("/customer/orders").with(user(customer))
                        .param("status", "CONFIRMED").param("fromDate", "2026-10-18")
                        .param("toDate", "2026-10-18").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/order-list"))
                .andExpect(content().string(containsString("ORD-SNAPSHOT")))
                .andExpect(content().string(containsString("Trang 2 / 3")))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(containsString("fromDate=2026-10-18")))
                .andExpect(content().string(containsString("status=CONFIRMED")));

        verify(orderService).getCustomerOrders(7L, OrderStatus.CONFIRMED,
                LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18), PageRequest.of(1, 1));
    }

    @ParameterizedTest
    @CsvSource({"status,UNKNOWN", "fromDate,not-a-date", "fromDate,2026-02-30", "fromDate,0000-01-01",
            "toDate,+10000-01-01", "page,-1", "page,abc", "page,2147483648", "size,0", "size,101", "size,abc"})
    void invalidFiltersRenderUsefulErrorWithoutExecutingQuery(String parameter, String value) throws Exception {
        mvc.perform(get("/customer/orders").with(user(customer)).param(parameter, value))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/order-list"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("role=\"alert\"")));
        verifyNoCustomerOrderQuery();
    }

    @Test
    void reversedDateRangeDoesNotQueryOrders() throws Exception {
        mvc.perform(get("/customer/orders").with(user(customer))
                        .param("fromDate", "2026-10-19").param("toDate", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "Ngày bắt đầu không được sau ngày kết thúc."));
        verifyNoCustomerOrderQuery();
    }

    @Test
    void pageOffsetOverflowIsRejectedBeforeJpaQuery() throws Exception {
        mvc.perform(get("/customer/orders").with(user(customer))
                        .param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "Số trang vượt quá giới hạn cho phép."));
        verifyNoCustomerOrderQuery();
    }

    @Test
    void detailUsesHistoricalSnapshotsAndSortsHistoryWithoutPublishingInternalNotes() throws Exception {
        Order order = order();
        order.setStatusHistories(List.of(
                OrderStatusHistory.builder().id(2L).oldStatus(OrderStatus.PENDING_CONFIRMATION)
                        .newStatus(OrderStatus.CONFIRMED).changedAt(LocalDateTime.of(2026, 10, 18, 10, 0))
                        .reason("INTERNAL ONLY SECRET")
                        .changedBy(User.builder().id(9L).fullName("STAFF PRIVATE NAME").build()).build(),
                OrderStatusHistory.builder().id(1L).newStatus(OrderStatus.PENDING_CONFIRMATION)
                        .changedAt(LocalDateTime.of(2026, 10, 18, 9, 0)).build()));
        when(orderService.getOrderDetailForCustomer(12L, 7L)).thenReturn(order);

        String html = mvc.perform(get("/customer/orders/12").with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tên lưu lúc đặt")))
                .andExpect(content().string(containsString("SKU-SNAPSHOT")))
                .andExpect(content().string(containsString("Mã giảm giá đã dùng: OLD-VOUCHER")))
                .andExpect(content().string(containsString("Chưa thanh toán")))
                .andExpect(content().string(not(containsString("INTERNAL ONLY SECRET"))))
                .andExpect(content().string(not(containsString("STAFF PRIVATE NAME"))))
                .andExpect(content().string(containsString("Nhân viên cửa hàng")))
                .andReturn().getResponse().getContentAsString();
        String snapshotLine = Pattern.compile("<li\\b[^>]*>.*?</li>", Pattern.DOTALL)
                .matcher(html).results().map(MatchResult::group)
                .filter(line -> line.contains("Tên lưu lúc đặt"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing snapshot order line"));
        assertThat(snapshotLine).containsPattern("(?s)<strong\\b[^>]*>\\s*246,912 đ\\s*</strong>");
        assertThat(html).contains("18/10/2026 09:00", "18/10/2026 10:00");
        assertThat(html.indexOf("18/10/2026 09:00")).isLessThan(html.indexOf("18/10/2026 10:00"));
        verify(orderService).getOrderDetailForCustomer(12L, 7L);
    }

    @Test
    void missingAndNonOwnedDetailsReturnSameFriendly404() throws Exception {
        when(orderService.getOrderDetailForCustomer(12L, 7L))
                .thenThrow(new IllegalArgumentException("Sensitive owner-specific message"));
        mvc.perform(get("/customer/orders/12").with(user(customer)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(not(containsString("Sensitive owner-specific message"))));
    }

    @ParameterizedTest
    @CsvSource({"COD,PENDING_CONFIRMATION,UNPAID,true", "COD,PENDING_CONFIRMATION,PAID,false",
            "COD,PENDING_PAYMENT,UNPAID,false", "VNPAY,PENDING_PAYMENT,UNPAID,true",
            "VNPAY,PENDING_PAYMENT,FAILED,true", "VNPAY,PENDING_PAYMENT,PAID,false",
            "VNPAY,CONFIRMED,PAID,false", "COD,SHIPPING,UNPAID,false", "COD,CANCELLED,CANCELLED,false"})
    void cancelFormReflectsPaymentMethodStatusAndPaymentState(PaymentMethod method, OrderStatus status,
                                                              PaymentStatus paymentStatus, boolean allowed) throws Exception {
        Order order = order();
        order.setPaymentMethod(method);
        order.setOrderStatus(status);
        order.setPaymentStatus(paymentStatus);
        when(orderService.getOrderDetailForCustomer(12L, 7L)).thenReturn(order);
        String html = mvc.perform(get("/customer/orders/12").with(user(customer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long cancelForms = Pattern.compile(
                        "<form\\b[^>]*\\saction\\s*=\\s*([\"'])/customer/orders/12/cancel\\1[^>]*>",
                        Pattern.CASE_INSENSITIVE)
                .matcher(html).results().count();
        assertThat(cancelForms).isEqualTo(allowed ? 1L : 0L);
    }

    @Test
    void cancelReasonIsTrimmedAndSentWithAuthenticatedOwner() throws Exception {
        when(orderService.getOrderDetailForCustomer(12L, 7L)).thenReturn(order());
        mvc.perform(post("/customer/orders/12/cancel").with(user(customer)).with(csrf())
                        .param("reason", "  Đổi nhu cầu  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/orders/12"))
                .andExpect(flash().attributeExists("successMessage"));
        verify(orderService).customerCancelOrder(12L, 7L, "Đổi nhu cầu");
    }

    @Test
    void oversizedCancelReasonDoesNotCallMutation() throws Exception {
        when(orderService.getOrderDetailForCustomer(12L, 7L)).thenReturn(order());
        mvc.perform(post("/customer/orders/12/cancel").with(user(customer)).with(csrf())
                        .param("reason", "x".repeat(1001)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        verify(orderService, never()).customerCancelOrder(anyLong(), anyLong(), anyString());
    }

    @Test
    void nonOwnedCancelDoesNotRevealOrderAndDoesNotCallMutation() throws Exception {
        when(orderService.getOrderDetailForCustomer(12L, 7L))
                .thenThrow(new IllegalArgumentException("Not your order"));
        mvc.perform(post("/customer/orders/12/cancel").with(user(customer)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
        verify(orderService, never()).customerCancelOrder(anyLong(), anyLong(), anyString());
    }

    @Test
    void cancelStateChangeFailureBecomesUserFacingMessage() throws Exception {
        when(orderService.getOrderDetailForCustomer(12L, 7L)).thenReturn(order());
        doThrow(new IllegalStateException("Đơn đã được xử lý, không thể hủy."))
                .when(orderService).customerCancelOrder(12L, 7L, "Khách hàng yêu cầu hủy đơn");
        mvc.perform(post("/customer/orders/12/cancel").with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Đơn đã được xử lý, không thể hủy."));
    }

    private void verifyNoCustomerOrderQuery() {
        verify(orderService, never()).getCustomerOrders(anyLong(), nullable(OrderStatus.class),
                nullable(LocalDate.class), nullable(LocalDate.class), any(Pageable.class));
    }

    private Order order() {
        Order order = Order.builder().id(12L).orderCode("ORD-SNAPSHOT")
                .recipientName("Người nhận đã lưu").recipientPhone("0901234567")
                .shippingProvince("Tỉnh").shippingDistrict("Huyện").shippingWard("Phường")
                .shippingAddressLine("Địa chỉ đã lưu").voucherCodeSnapshot("OLD-VOUCHER")
                .subtotalAmount(new BigDecimal("246912")).discountAmount(new BigDecimal("10000"))
                .shippingFee(new BigDecimal("30000")).totalAmount(new BigDecimal("266912"))
                .orderStatus(OrderStatus.PENDING_CONFIRMATION).paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID).createdAt(LocalDateTime.of(2026, 10, 18, 8, 0)).build();
        order.setItems(List.of(OrderItem.builder().productNameSnapshot("Tên lưu lúc đặt")
                .skuCodeSnapshot("SKU-SNAPSHOT").variantNameSnapshot("Mẫu đã lưu")
                .unitPrice(new BigDecimal("123456")).quantity(2).lineTotal(BigDecimal.ONE).build()));
        return order;
    }
}
