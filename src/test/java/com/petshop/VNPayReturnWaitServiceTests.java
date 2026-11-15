package com.petshop;

import com.petshop.entity.enums.TransactionStatus;
import com.petshop.repository.PaymentTransactionRepository;
import com.petshop.service.VNPayReturnWaitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VNPayReturnWaitServiceTests {
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final VNPayReturnWaitService service = new VNPayReturnWaitService(payments);

    @AfterEach
    void stopExecutor() throws Exception {
        service.shutdown();
        assertThat(executor().awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"SUCCESS", "FAILED", "CANCELLED", "EXPIRED"})
    void storedFinalStatusRedirectsToTheVerifiedOrderAndStopsPolling(TransactionStatus status) throws Exception {
        when(payments.findStatusForReturn("verified-reference", 12L, 3L)).thenReturn(Optional.of(status));
        ModelAndView view = await(service.waitForResult(12L, 3L, "verified-reference"), 3);
        assertThat(view.getViewName()).isEqualTo("redirect:/customer/orders/12");
        assertStoppedPolling();
        verify(payments).findStatusForReturn("verified-reference", 12L, 3L);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void laterCommittedIpnStatusIsReadAgainAndRedirectsWithoutWritingPaymentData() throws Exception {
        when(payments.findStatusForReturn("verified-reference", 12L, 3L))
                .thenReturn(Optional.of(TransactionStatus.PENDING), Optional.of(TransactionStatus.SUCCESS));
        ModelAndView view = await(service.waitForResult(12L, 3L, "verified-reference"), 3);
        assertThat(view.getViewName()).isEqualTo("redirect:/customer/orders/12");
        assertStoppedPolling();
        verify(payments, times(2)).findStatusForReturn("verified-reference", 12L, 3L);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void missingTransactionShowsPendingInsteadOfGuessingAPaymentResult() throws Exception {
        when(payments.findStatusForReturn("verified-reference", 12L, 3L)).thenReturn(Optional.empty());
        assertPending(await(service.waitForResult(12L, 3L, "verified-reference"), 3));
        assertStoppedPolling();
        verify(payments).findStatusForReturn("verified-reference", 12L, 3L);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void repositoryFailureShowsPendingAndRemovesItsScheduledTask() throws Exception {
        when(payments.findStatusForReturn("verified-reference", 12L, 3L))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("test timeout"));
        assertPending(await(service.waitForResult(12L, 3L, "verified-reference"), 3));
        assertStoppedPolling();
        verify(payments).findStatusForReturn("verified-reference", 12L, 3L);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void pendingTransactionFallsBackAfterTheExistingEightSecondWaitAndStopsPolling() throws Exception {
        when(payments.findStatusForReturn("verified-reference", 12L, 3L)).thenReturn(Optional.of(TransactionStatus.PENDING));
        long started = System.nanoTime();
        assertPending(await(service.waitForResult(12L, 3L, "verified-reference"), 11));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isGreaterThanOrEqualTo(7900);
        assertStoppedPolling();
        verify(payments, atLeast(2)).findStatusForReturn("verified-reference", 12L, 3L);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void completionByTheHttpLayerStopsAnOtherwisePendingPoll() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        when(payments.findStatusForReturn("verified-reference", 12L, 3L)).thenAnswer(invocation -> {
            firstRead.countDown();
            return Optional.of(TransactionStatus.PENDING);
        });
        DeferredResult<ModelAndView> result = service.waitForResult(12L, 3L, "verified-reference");
        assertThat(firstRead.await(3, TimeUnit.SECONDS)).isTrue();
        result.setResult(new ModelAndView("customer/payment-pending"));
        assertStoppedPolling();
    }

    @Test
    void applicationShutdownTerminatesTheExecutorAndDropsPendingPollingTasks() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        when(payments.findStatusForReturn("verified-reference", 12L, 3L)).thenAnswer(invocation -> {
            firstRead.countDown();
            return Optional.of(TransactionStatus.PENDING);
        });
        service.waitForResult(12L, 3L, "verified-reference");
        assertThat(firstRead.await(3, TimeUnit.SECONDS)).isTrue();
        service.shutdown();
        assertThat(executor().awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        assertThat(executor().isShutdown()).isTrue();
        assertThat(executor().getQueue()).isEmpty();
    }

    private ModelAndView await(DeferredResult<ModelAndView> result, int seconds) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        result.setResultHandler(value -> completed.countDown());
        assertThat(completed.await(seconds, TimeUnit.SECONDS)).isTrue();
        return (ModelAndView) result.getResult();
    }

    private void assertPending(ModelAndView view) {
        assertThat(view.getViewName()).isEqualTo("customer/payment-pending");
        assertThat(view.getModel()).containsEntry("orderId", 12L);
    }

    private ScheduledThreadPoolExecutor executor() {
        return (ScheduledThreadPoolExecutor) ReflectionTestUtils.getField(service, "executor");
    }

    private void assertStoppedPolling() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while ((!executor().getQueue().isEmpty() || executor().getActiveCount() != 0) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(executor().getQueue()).isEmpty();
        assertThat(executor().getActiveCount()).isZero();
    }
}
