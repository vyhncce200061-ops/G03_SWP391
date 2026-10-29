package com.petshop;

import com.petshop.config.VnPayProperties;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.OrderService;
import com.petshop.service.VnPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnPayServiceTests {
    private static final String SECRET = "fake-test-secret-only";
    private static final String VECTOR_HASH = "cf6877fdfbd2905ecbb3df9532e97390f2e7e3b236d6573f85f4dc1702a10fad1ba77e1437db419986c8dc0cc3ddef4656613e268f3aeb449fed461d2cdb2446";
    @Mock PaymentTransactionRepository transactions;
    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository histories;
    @Mock OrderService orderService;
    VnPayProperties properties;
    VnPayService service;
    Order order;
    PaymentTransaction transaction;

    @BeforeEach
    void setup() {
        properties = new VnPayProperties();
        properties.setTmnCode("TESTCODE");
        properties.setHashSecret(SECRET);
        properties.setReturnUrl("http://localhost:8081/payment/vnpay-return");
        service = new VnPayService(properties, transactions, orders, histories, orderService);
        User customer = User.builder().id(7L).build();
        order = Order.builder().id(10L).orderCode("ORD-TEST").customer(customer)
                .totalAmount(new BigDecimal("130000.00")).paymentMethod(PaymentMethod.VNPAY)
                .orderStatus(OrderStatus.PENDING_PAYMENT).paymentStatus(PaymentStatus.UNPAID).build();
        transaction = PaymentTransaction.builder().id(20L).transactionCode("TXN-TEST-1")
                .providerOrderReference("TXN-TEST-1").order(order).customer(customer)
                .amount(new BigDecimal("130000.00")).paymentMethod(PaymentMethod.VNPAY)
                .paymentEnvironment(PaymentEnvironment.SANDBOX).transactionStatus(TransactionStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(15)).build();
    }

    @Test
    void noCredentialsDisablesVnpayBeforeAnyOrderLookup() {
        properties.setHashSecret("");
        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.createPaymentUrl(order, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(transactions, orders, histories, orderService);
    }

    @Test
    void malformedReturnUrlIsNotConfigured() {
        properties.setReturnUrl("javascript:alert(1)");
        assertThat(service.isConfigured()).isFalse();
        properties.setReturnUrl("https://user:password@example.com/payment/vnpay-return");
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void createsEncodedSignedSandboxUrlWithExactAmountAndTransactionReference() throws Exception {
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(transactions.findByOrderIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(transaction));
        String url = service.createPaymentUrl(order, "::1");
        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        Map<String, String[]> query = parse(url);
        assertThat(query.get("vnp_Amount")).containsExactly("13000000");
        assertThat(query.get("vnp_TxnRef")).containsExactly("TXN-TEST-1");
        assertThat(query.get("vnp_ReturnUrl")).containsExactly(properties.getReturnUrl());
        assertThat(query.get("vnp_IpAddr")).containsExactly("::1");
        assertThat(query.get("vnp_CreateDate")[0]).matches("[0-9]{14}");
        String received = query.remove("vnp_SecureHash")[0];
        assertThat(signed(query).get("vnp_SecureHash")[0]).isEqualTo(received);
        verify(transactions).save(transaction);
    }

    @Test
    void rejectsFractionalDongWithoutSilentlyRounding() {
        order.setTotalAmount(new BigDecimal("130000.50"));
        transaction.setAmount(order.getTotalAmount());
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(transactions.findByOrderIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(transaction));
        assertThatThrownBy(() -> service.createPaymentUrl(order, "127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void knownIndependentSignatureVectorConfirmsPaymentAndRecordsHistory() {
        mockCallbackLookup();
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "00");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getPaidAt()).isNotNull();
        assertThat(transaction.getCallbackReceivedAt()).isNotNull();
        assertThat(transaction.getSignatureVerified()).isTrue();
        assertThat(transaction.getProviderTransactionId()).isEqualTo("123456");
        verify(histories).save(argThat(h -> h.getOldStatus() == OrderStatus.PENDING_PAYMENT
                && h.getNewStatus() == OrderStatus.CONFIRMED));
        var inOrder = inOrder(transactions, orders);
        inOrder.verify(transactions).findOrderIdByTransactionCode("TXN-TEST-1");
        inOrder.verify(orders).findByIdForUpdate(10L);
        inOrder.verify(transactions).findByTransactionCode("TXN-TEST-1");
        verifyNoInteractions(orderService);
    }

    @Test
    void tamperedAmountIsRejectedBeforeDatabaseAccess() {
        Map<String, String[]> query = vector();
        query.put("vnp_Amount", new String[]{"1"});
        assertThat(service.processIpn(query)).containsEntry("RspCode", "97");
        verifyNoInteractions(transactions, orders, histories, orderService);
    }

    @Test
    void duplicateSignedParametersAreRejectedBeforeDatabaseAccess() {
        Map<String, String[]> query = vector();
        query.put("vnp_TxnRef", new String[]{"TXN-TEST-1", "ANOTHER-ORDER"});
        assertThat(service.processIpn(query)).containsEntry("RspCode", "97");
        verifyNoInteractions(transactions, orders, histories, orderService);
    }

    @Test
    void signedWrongMerchantIsRejected() throws Exception {
        Map<String, String[]> query = vector();
        query.put("vnp_TmnCode", new String[]{"OTHER-MERCHANT"});
        assertThat(service.processIpn(signed(query))).containsEntry("RspCode", "99");
        verifyNoInteractions(transactions, orders, histories, orderService);
    }

    @Test
    void signedWrongAmountDoesNotChangeOrder() throws Exception {
        mockCallbackLookup();
        Map<String, String[]> query = vector();
        query.put("vnp_Amount", new String[]{"1"});
        assertThat(service.processIpn(signed(query))).containsEntry("RspCode", "04");
        assertPending();
        verifyNoInteractions(histories, orderService);
        verify(transactions, never()).save(any());
    }

    @Test
    void unknownReferenceDoesNotExposeAnOrder() {
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "01");
        verifyNoInteractions(orders, histories, orderService);
    }

    @Test
    void repeatedSuccessWritesHistoryOnlyOnce() {
        mockCallbackLookup();
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "00");
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "02");
        verify(histories, times(1)).save(any());
        verify(transactions, times(1)).save(transaction);
        verifyNoInteractions(orderService);
    }

    @Test
    void successAfterCustomerCancellationNeverReopensOrRestoresAgain() {
        mockCallbackLookup();
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        transaction.setTransactionStatus(TransactionStatus.CANCELLED);
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "02");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(transaction.getSignatureVerified()).isTrue();
        assertThat(transaction.getResponseCode()).isEqualTo("00");
        assertThat(transaction.getFailureReason()).contains("đối soát");
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "02");
        verify(transactions, times(1)).save(transaction);
        verifyNoInteractions(histories, orderService);
        verify(orders, never()).save(any());
    }

    @Test
    void expiredPendingOrderIsCancelledThroughSharedReservationPath() {
        mockCallbackLookup();
        transaction.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "02");
        verify(orderService).cancelPendingPaymentOrder(eq(10L), eq(CancelType.PAYMENT_EXPIRED), anyString());
        verifyNoInteractions(histories);
        verify(orders, never()).save(any());
    }

    @Test
    void failedIpnCancelsReservationOnceAndNeverStoresProviderZero() throws Exception {
        mockCallbackLookup();
        doAnswer(invocation -> {
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.CANCELLED);
            transaction.setTransactionStatus(TransactionStatus.CANCELLED);
            return null;
        }).when(orderService).cancelPendingPaymentOrder(eq(10L), eq(CancelType.PAYMENT_FAILED), anyString());
        Map<String, String[]> query = vector();
        query.put("vnp_ResponseCode", new String[]{"51"});
        query.put("vnp_TransactionStatus", new String[]{"02"});
        query.put("vnp_TransactionNo", new String[]{"0"});
        query = signed(query);
        assertThat(service.processIpn(query)).containsEntry("RspCode", "00");
        assertThat(service.processIpn(query)).containsEntry("RspCode", "02");
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getProviderTransactionId()).isNull();
        verify(orderService, times(1)).cancelPendingPaymentOrder(eq(10L), eq(CancelType.PAYMENT_FAILED), anyString());
    }

    @Test
    void requiresBothGatewaySuccessCodes() throws Exception {
        mockCallbackLookup();
        Map<String, String[]> query = vector();
        query.put("vnp_TransactionStatus", new String[]{"02"});
        assertThat(service.processIpn(signed(query))).containsEntry("RspCode", "00");
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(order.getPaymentStatus()).isNotEqualTo(PaymentStatus.PAID);
        verify(orderService).cancelPendingPaymentOrder(eq(10L), eq(CancelType.PAYMENT_FAILED), anyString());
    }

    @Test
    void gatewayAbortUsesPaymentCancelledCause() throws Exception {
        mockCallbackLookup();
        Map<String, String[]> query = vector();
        query.put("vnp_ResponseCode", new String[]{"24"});
        query.put("vnp_TransactionStatus", new String[]{"02"});
        assertThat(service.processIpn(signed(query))).containsEntry("RspCode", "00");
        verify(orderService).cancelPendingPaymentOrder(eq(10L), eq(CancelType.PAYMENT_CANCELLED), anyString());
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.CANCELLED);
    }

    @Test
    void duplicateProviderTransactionIdIsRejectedBeforeMutation() {
        mockCallbackLookup();
        when(transactions.existsByProviderTransactionIdAndIdNot("123456", 20L)).thenReturn(true);
        assertThat(service.processIpn(vector())).containsEntry("RspCode", "99");
        assertPending();
        verifyNoInteractions(histories, orderService);
    }

    @Test
    void verifiedReturnForOwnerIsReadOnlyAndDoesNotApplyIpn() {
        when(transactions.findByTransactionCode("TXN-TEST-1")).thenReturn(Optional.of(transaction));
        assertThat(service.findOwnedOrderIdForReturn(vector(), 7L)).contains(10L);
        assertPending();
        verifyNoInteractions(orders, histories, orderService);
        verify(transactions, never()).save(any());
    }

    @Test
    void verifiedReturnDoesNotRevealAnotherCustomersOrder() {
        when(transactions.findByTransactionCode("TXN-TEST-1")).thenReturn(Optional.of(transaction));
        assertThat(service.findOwnedOrderIdForReturn(vector(), 8L)).isEmpty();
        verifyNoInteractions(orders, histories, orderService);
    }

    @Test
    void tamperedOrAnonymousReturnDoesNotReadDatabase() {
        Map<String, String[]> query = vector();
        query.put("vnp_SecureHash", new String[]{"bad"});
        assertThat(service.findOwnedOrderIdForReturn(query, 7L)).isEmpty();
        assertThat(service.findOwnedOrderIdForReturn(vector(), null)).isEmpty();
        verifyNoInteractions(transactions, orders, histories, orderService);
    }

    private void assertPending() {
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    private void mockCallbackLookup() {
        when(transactions.findOrderIdByTransactionCode("TXN-TEST-1")).thenReturn(Optional.of(10L));
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(transactions.findByTransactionCode("TXN-TEST-1")).thenReturn(Optional.of(transaction));
    }

    private Map<String, String[]> vector() {
        Map<String, String[]> query = new TreeMap<>();
        query.put("vnp_Amount", new String[]{"13000000"});
        query.put("vnp_BankCode", new String[]{"NCB"});
        query.put("vnp_CardType", new String[]{"ATM"});
        query.put("vnp_ResponseCode", new String[]{"00"});
        query.put("vnp_TmnCode", new String[]{"TESTCODE"});
        query.put("vnp_TransactionNo", new String[]{"123456"});
        query.put("vnp_TransactionStatus", new String[]{"00"});
        query.put("vnp_TxnRef", new String[]{"TXN-TEST-1"});
        query.put("vnp_SecureHash", new String[]{VECTOR_HASH});
        return query;
    }

    private Map<String, String[]> signed(Map<String, String[]> query) throws Exception {
        Map<String, String[]> copy = new TreeMap<>(query);
        copy.remove("vnp_SecureHash");
        String canonical = copy.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(e.getValue()[0], StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        copy.put("vnp_SecureHash", new String[]{HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)))});
        return copy;
    }

    private Map<String, String[]> parse(String url) {
        Map<String, String[]> query = new TreeMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] entry = pair.split("=", 2);
            query.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                    new String[]{URLDecoder.decode(entry[1], StandardCharsets.UTF_8)});
        }
        return query;
    }
}
