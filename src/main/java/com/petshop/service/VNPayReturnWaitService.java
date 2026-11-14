package com.petshop.service;

import com.petshop.entity.enums.TransactionStatus;
import com.petshop.repository.PaymentTransactionRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayReturnWaitService {

    private static final long WAIT_SECONDS = 8;
    private static final long POLL_INTERVAL_MS = 500;

    private final PaymentTransactionRepository paymentTransactionRepository;

    // Dùng chung cho các request, không tạo executor riêng cho mỗi khách.
    private final ScheduledThreadPoolExecutor executor = createExecutor();

    private static ScheduledThreadPoolExecutor createExecutor() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(4);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    public DeferredResult<ModelAndView> waitForResult(
            Long orderId,
            Long customerId,
            String transactionCode
    ) {
        // Timeout HTTP dự phòng, hơi dài hơn thời gian kiểm tra nghiệp vụ.
        DeferredResult<ModelAndView> result = new DeferredResult<>(9_000L);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);

        AtomicReference<ScheduledFuture<?>> taskRef = new AtomicReference<>();

        Runnable cancelTask = () -> {
            ScheduledFuture<?> task = taskRef.get();
            if (task != null) {
                task.cancel(false);
            }
        };

        result.onTimeout(() -> {
            result.setResult(pendingPage(orderId));
            cancelTask.run();
        });

        result.onError(error -> cancelTask.run());
        result.onCompletion(cancelTask);

        Runnable checkStatus = () -> {
            if (result.isSetOrExpired()) {
                cancelTask.run();
                return;
            }

            if (System.nanoTime() >= deadline) {
                result.setResult(pendingPage(orderId));
                cancelTask.run();
                return;
            }

            try {
                Optional<TransactionStatus> status =
                        paymentTransactionRepository.findStatusForReturn(
                                transactionCode, orderId, customerId
                        );

                if (status.isEmpty()) {
                    // Không còn đọc được giao dịch đã xác minh:
                    // không tự suy đoán thành công hoặc thất bại.
                    result.setResult(pendingPage(orderId));
                    cancelTask.run();
                    return;
                }

                if (status.get() != TransactionStatus.PENDING) {
                    // SUCCESS, FAILED, CANCELLED hoặc EXPIRED:
                    // đã có kết quả được lưu, detail sẽ hiển thị tương ứng.
                    result.setResult(new ModelAndView(
                            "redirect:/customer/orders/" + orderId
                    ));
                    cancelTask.run();
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "Unable to read VNPay return status for order {}",
                        orderId, ex
                );

                result.setResult(pendingPage(orderId));
                cancelTask.run();
            }
        };

        ScheduledFuture<?> task = executor.scheduleWithFixedDelay(
                checkStatus,
                0,
                POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        taskRef.set(task);

        // Xử lý trường hợp lượt kiểm tra đầu đã kết thúc
        // trước khi task được gán vào taskRef.
        if (result.isSetOrExpired()) {
            task.cancel(false);
        }

        return result;
    }

    private ModelAndView pendingPage(Long orderId) {
        ModelAndView view =
                new ModelAndView("customer/payment-pending");
        view.addObject("orderId", orderId);
        return view;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
