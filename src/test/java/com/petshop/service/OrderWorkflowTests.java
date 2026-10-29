package com.petshop.service;

import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/** Exercises the real transaction boundaries, not just calls to repository mocks. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:orderworkflow;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@ActiveProfiles("test")
class OrderWorkflowTests {
    @Autowired private OrderService orders;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @SpyBean private ProductVariantRepository variants;
    @Autowired private CartRepository carts;
    @Autowired private CartItemRepository cartItems;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private OrderStatusHistoryRepository histories;
    @Autowired private PaymentTransactionRepository payments;
    @Autowired private VoucherRepository vouchers;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    @Test
    void selectedLinesOnlyDetermineTotalsSnapshotsStockAndCartRemoval() {
        Fixture f = fixture(true, false, false);
        Order placed = orders.checkoutOrder(f.customerId(), request(PaymentMethod.COD));
        assertMoney("200000", placed.getSubtotalAmount());
        assertMoney("230000", placed.getTotalAmount());
        assertEquals(1, placed.getItems().size());
        assertTrue(placed.getOrderCode().length() <= 40);
        inTransaction(() -> {
            assertEquals(8, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(10, variants.findById(f.secondId()).orElseThrow().getStockQuantity());
            List<CartItem> remaining = cartItems.findByCartId(f.cartId());
            assertEquals(2, remaining.size());
            assertTrue(remaining.stream().noneMatch(item -> item.getVariant().getId().equals(f.firstId())));
            ProductVariant edited = variants.findById(f.firstId()).orElseThrow();
            edited.setPrice(new BigDecimal("999999"));
            edited.setVariantName("Tên đã thay đổi");
            return null;
        });
        Order detail = orders.getOrderDetailForCustomer(placed.getId(), f.customerId());
        assertMoney("100000", detail.getItems().get(0).getUnitPrice());
        assertEquals("SKU 1", detail.getItems().get(0).getVariantNameSnapshot());
        assertEquals("Customer", detail.getStatusHistories().get(0).getChangedBy().getFullName());
        assertEquals(placed.getId(), orders.getOrderByCodeForCustomer(placed.getOrderCode(), f.customerId()).getId());
        assertThrows(IllegalArgumentException.class,
                () -> orders.getOrderByCodeForCustomer(placed.getOrderCode(), -1L));
    }

    @Test
    void noSelectionCannotCreateAnOrder() {
        Fixture f = fixture(false, false, false);
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), request(PaymentMethod.COD)));
        assertEquals(0, orders.getCustomerOrders(f.customerId(), null, PageRequest.of(0, 10)).getTotalElements());
        inTransaction(() -> {
            assertEquals(3, cartItems.findByCartId(f.cartId()).size());
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            return null;
        });
    }

    @Test
    void nonPositiveQuantityIsRejectedWithoutStockOrOrderChanges() {
        Fixture f = fixture(true, false, false);
        inTransaction(() -> {
            CartItem line = cartItems.findByCartIdAndVariantId(f.cartId(), f.firstId()).orElseThrow();
            line.setQuantity(0);
            return null;
        });
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), request(PaymentMethod.COD)));
        assertEquals(0, orders.getCustomerOrders(f.customerId(), null, PageRequest.of(0, 10)).getTotalElements());
        inTransaction(() -> {
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            return null;
        });
    }

    @Test
    void insufficientSelectedSkuPreservesAllOtherStockAndVoucherQuota() {
        Fixture f = fixture(true, true, false);
        Long voucherId = voucher();
        inTransaction(() -> {
            variants.findById(f.secondId()).orElseThrow().setStockQuantity(0);
            return null;
        });
        CheckoutSubmitDto request = request(PaymentMethod.COD);
        request.setVoucherCode(inTransaction(() -> vouchers.findById(voucherId).orElseThrow().getCode()));
        assertThrows(IllegalStateException.class, () -> orders.checkoutOrder(f.customerId(), request));
        assertEquals(0, orders.getCustomerOrders(f.customerId(), null, PageRequest.of(0, 10)).getTotalElements());
        inTransaction(() -> {
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(0, vouchers.findById(voucherId).orElseThrow().getUsedCount());
            assertEquals(3, cartItems.findByCartId(f.cartId()).size());
            return null;
        });
    }

    @Test
    void failedStockWriteRollsBackEarlierDeductionOrderAndVoucherReservation() {
        Fixture f = fixture(true, true, false);
        Long voucherId = voucher();
        CheckoutSubmitDto request = request(PaymentMethod.COD);
        request.setVoucherCode(inTransaction(() -> vouchers.findById(voucherId).orElseThrow().getCode()));
        // Simulate a failed conditional write after the first SKU and voucher quota
        // have been updated. All other persistence calls still use the real database.
        doReturn(0).when(variants).deductStockAtomic(f.secondId(), 2);
        assertThrows(IllegalStateException.class, () -> orders.checkoutOrder(f.customerId(), request));
        verify(variants).deductStockAtomic(f.firstId(), 2);
        verify(variants).deductStockAtomic(f.secondId(), 2);
        assertEquals(0, orders.getCustomerOrders(f.customerId(), null, PageRequest.of(0, 10)).getTotalElements());
        inTransaction(() -> {
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(10, variants.findById(f.secondId()).orElseThrow().getStockQuantity());
            assertEquals(0, vouchers.findById(voucherId).orElseThrow().getUsedCount());
            assertEquals(3, cartItems.findByCartId(f.cartId()).size());
            return null;
        });
    }

    @Test
    void invalidDtoCannotReachDatabaseWrites() {
        Fixture f = fixture(true, false, false);
        CheckoutSubmitDto missingMethod = request(null);
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), missingMethod));
        CheckoutSubmitDto tooLong = request(PaymentMethod.COD);
        tooLong.setShippingDistrict("x".repeat(101));
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), tooLong));
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), null));
        assertEquals(0, orders.getCustomerOrders(f.customerId(), null, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void cancellationChecksOwnershipAndRestoresStockAndVoucherOnlyOnce() {
        Fixture f = fixture(true, false, false);
        Long voucherId = voucher();
        CheckoutSubmitDto request = request(PaymentMethod.COD);
        request.setVoucherCode(inTransaction(() -> vouchers.findById(voucherId).orElseThrow().getCode()));
        Order placed = orders.checkoutOrder(f.customerId(), request);
        assertMoney("180000", placed.getSubtotalAmount().subtract(placed.getDiscountAmount()));
        assertThrows(IllegalArgumentException.class, () -> orders.customerCancelOrder(placed.getId(), -1L, "Không phải chủ đơn"));
        orders.customerCancelOrder(placed.getId(), f.customerId(), "x".repeat(1000));
        assertThrows(IllegalStateException.class, () -> orders.customerCancelOrder(placed.getId(), f.customerId(), "Lặp lại"));
        inTransaction(() -> {
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(0, vouchers.findById(voucherId).orElseThrow().getUsedCount());
            Order cancelled = orderRepository.findById(placed.getId()).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, cancelled.getOrderStatus());
            assertEquals(PaymentStatus.CANCELLED, cancelled.getPaymentStatus());
            assertEquals(1000, cancelled.getCancelReason().length());
            assertEquals(2, cancelled.getStatusHistories().size());
            assertTrue(cancelled.getStatusHistories().stream().allMatch(h -> h.getReason().length() <= 1000));
            return null;
        });
    }

    @Test
    void activeOrdersConsumeCustomerVoucherLimitAndCancellationReleasesIt() {
        Fixture f = fixture(true, false, false);
        Long voucherId = voucher();
        CheckoutSubmitDto request = request(PaymentMethod.COD);
        request.setVoucherCode(inTransaction(() -> vouchers.findById(voucherId).orElseThrow().getCode()));
        Order first = orders.checkoutOrder(f.customerId(), request);
        addFirstSkuAgain(f);
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), request));
        orders.customerCancelOrder(first.getId(), f.customerId(), "Đổi đơn hàng");
        Order replacement = orders.checkoutOrder(f.customerId(), request);
        assertMoney("10000", replacement.getDiscountAmount());
        inTransaction(() -> {
            assertEquals(1, vouchers.findById(voucherId).orElseThrow().getUsedCount());
            assertEquals(9, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            return null;
        });
    }

    @Test
    void completedOrdersStillConsumeCustomerVoucherLimit() {
        Long staffId = staffActor();
        Fixture f = fixture(true, false, false);
        Long voucherId = voucher();
        CheckoutSubmitDto request = request(PaymentMethod.COD);
        request.setVoucherCode(inTransaction(() -> vouchers.findById(voucherId).orElseThrow().getCode()));
        Order first = orders.checkoutOrder(f.customerId(), request);
        orders.confirmOrder(first.getId(), staffId);
        orders.markPreparing(first.getId(), staffId);
        orders.markShipping(first.getId(), staffId);
        orders.markCompleted(first.getId(), staffId);
        addFirstSkuAgain(f);
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), request));
        inTransaction(() -> {
            assertEquals(1, vouchers.findById(voucherId).orElseThrow().getUsedCount());
            assertEquals(8, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(3, cartItems.findByCartId(f.cartId()).size());
            return null;
        });
    }

    @Test
    void missingOrInactiveVoucherLeavesCartStockAndOrderUnchanged() {
        Fixture f = fixture(true, false, false);
        CheckoutSubmitDto request = request(PaymentMethod.COD);
        request.setVoucherCode("MISSING-" + UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), request));
        Long voucherId = voucher();
        request.setVoucherCode(inTransaction(() -> {
            Voucher voucher = vouchers.findById(voucherId).orElseThrow();
            voucher.setStatus(VoucherStatus.INACTIVE);
            return voucher.getCode();
        }));
        assertThrows(IllegalArgumentException.class, () -> orders.checkoutOrder(f.customerId(), request));
        assertEquals(0, orders.getCustomerOrders(f.customerId(), null, PageRequest.of(0, 10)).getTotalElements());
        inTransaction(() -> {
            assertEquals(0, vouchers.findById(voucherId).orElseThrow().getUsedCount());
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(3, cartItems.findByCartId(f.cartId()).size());
            return null;
        });
    }

    private void addFirstSkuAgain(Fixture fixture) {
        inTransaction(() -> {
            cartItems.save(CartItem.builder().cart(carts.findById(fixture.cartId()).orElseThrow())
                    .variant(variants.findById(fixture.firstId()).orElseThrow()).quantity(1).isSelected(true).build());
            return null;
        });
    }

    @Test
    void vnpayCancelRequiresPendingTransactionAndCannotCancelSuccessfulPayment() {
        Fixture f = fixture(true, false, false);
        Order placed = orders.checkoutOrder(f.customerId(), request(PaymentMethod.VNPAY));
        inTransaction(() -> {
            PaymentTransaction transaction = payments.findByOrderIdOrderByCreatedAtDesc(placed.getId()).get(0);
            transaction.setTransactionStatus(TransactionStatus.FAILED);
            return null;
        });
        assertThrows(IllegalStateException.class, () -> orders.customerCancelOrder(placed.getId(), f.customerId(), "Hủy"));
        inTransaction(() -> {
            PaymentTransaction transaction = payments.findByOrderIdOrderByCreatedAtDesc(placed.getId()).get(0);
            transaction.setTransactionStatus(TransactionStatus.SUCCESS);
            return null;
        });
        assertThrows(IllegalStateException.class, () -> orders.customerCancelOrder(placed.getId(), f.customerId(), "Hủy"));
        inTransaction(() -> {
            assertEquals(8, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findById(placed.getId()).orElseThrow().getOrderStatus());
            return null;
        });
    }

    @Test
    void vnpayCancellationClosesEveryPendingAttempt() {
        Fixture f = fixture(true, false, false);
        Order placed = orders.checkoutOrder(f.customerId(), request(PaymentMethod.VNPAY));
        inTransaction(() -> {
            payments.save(PaymentTransaction.builder().order(orderRepository.findById(placed.getId()).orElseThrow())
                    .customer(users.findById(f.customerId()).orElseThrow()).transactionCode("EXTRA-" + UUID.randomUUID())
                    .paymentMethod(PaymentMethod.VNPAY).amount(placed.getTotalAmount())
                    .transactionStatus(TransactionStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(15)).build());
            return null;
        });
        orders.customerCancelOrder(placed.getId(), f.customerId(), "Hủy thanh toán");
        inTransaction(() -> {
            assertEquals(2, payments.findByOrderIdOrderByCreatedAtDesc(placed.getId()).size());
            assertTrue(payments.findByOrderIdOrderByCreatedAtDesc(placed.getId()).stream()
                    .allMatch(tx -> tx.getTransactionStatus() == TransactionStatus.CANCELLED));
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            return null;
        });
    }

    @Test
    void expiredPaymentCleanupIsIdempotent() {
        Fixture f = fixture(true, false, false);
        Order placed = orders.checkoutOrder(f.customerId(), request(PaymentMethod.VNPAY));
        inTransaction(() -> {
            payments.findByOrderIdOrderByCreatedAtDesc(placed.getId()).get(0).setExpiresAt(LocalDateTime.now().minusMinutes(1));
            return null;
        });
        orders.cancelExpiredPendingPaymentOrders();
        orders.cancelExpiredPendingPaymentOrders();
        inTransaction(() -> {
            Order cancelled = orderRepository.findById(placed.getId()).orElseThrow();
            assertEquals(CancelType.PAYMENT_EXPIRED, cancelled.getCancelType());
            assertEquals(2, cancelled.getStatusHistories().size());
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(TransactionStatus.EXPIRED, payments.findByOrderIdOrderByCreatedAtDesc(placed.getId()).get(0).getTransactionStatus());
            return null;
        });
    }

    @Test
    void expiryBatchAndCustomerCancellationCanRestoreSharedSkuWithoutDoubleRelease() throws Exception {
        Fixture f = fixture(true, false, false);
        Order first = orders.checkoutOrder(f.customerId(), request(PaymentMethod.VNPAY));
        addFirstSkuAgain(f);
        Order second = orders.checkoutOrder(f.customerId(), request(PaymentMethod.VNPAY));
        inTransaction(() -> {
            payments.findByOrderIdOrderByCreatedAtDesc(first.getId()).get(0)
                    .setExpiresAt(LocalDateTime.now().minusMinutes(1));
            payments.findByOrderIdOrderByCreatedAtDesc(second.getId()).get(0)
                    .setExpiresAt(LocalDateTime.now().minusMinutes(1));
            return null;
        });
        runConcurrently(() -> {
            orders.cancelExpiredPendingPaymentOrders();
            return true;
        }, () -> {
            try { orders.customerCancelOrder(second.getId(), f.customerId(), "Hủy cùng lúc hết hạn"); return true; }
            catch (IllegalStateException expected) { return false; }
        });
        inTransaction(() -> {
            assertEquals(OrderStatus.CANCELLED, orderRepository.findById(first.getId()).orElseThrow().getOrderStatus());
            assertEquals(OrderStatus.CANCELLED, orderRepository.findById(second.getId()).orElseThrow().getOrderStatus());
            assertEquals(2, orderRepository.findById(first.getId()).orElseThrow().getStatusHistories().size());
            assertEquals(2, orderRepository.findById(second.getId()).orElseThrow().getStatusHistories().size());
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            return null;
        });
    }

    @Test
    void staffFlowPreservesCodPaymentRulesAndBlocksLaterCustomerCancellation() {
        Long staffId = staffActor();
        Fixture f = fixture(true, false, false);
        Order placed = orders.checkoutOrder(f.customerId(), request(PaymentMethod.COD));
        orders.confirmOrder(placed.getId(), staffId);
        assertThrows(IllegalStateException.class, () -> orders.customerCancelOrder(placed.getId(), f.customerId(), "Muộn"));
        orders.markPreparing(placed.getId(), staffId);
        orders.markShipping(placed.getId(), staffId);
        orders.markCompleted(placed.getId(), staffId);
        Order complete = orders.getOrderDetailForCustomer(placed.getId(), f.customerId());
        assertEquals(OrderStatus.COMPLETED, complete.getOrderStatus());
        assertEquals(PaymentStatus.PAID, complete.getPaymentStatus());
        assertNotNull(complete.getCompletedAt());
        assertEquals(5, complete.getStatusHistories().size());
        assertThrows(IllegalStateException.class, () -> orders.markCompleted(placed.getId(), staffId));
        assertThrows(IllegalStateException.class, () -> orders.staffRejectOrder(placed.getId(), staffId, "Muộn"));
    }

    @Test
    void dateFiltersAreInclusiveAndCustomerScoped() {
        Fixture f = fixture(true, false, false);
        Order placed = orders.checkoutOrder(f.customerId(), request(PaymentMethod.COD));
        inTransaction(() -> {
            // createdAt is immutable in JPA, so use SQL to exercise an exact boundary.
            entityManager.createNativeQuery("UPDATE dbo.Orders SET CreatedAt = :createdAt WHERE Id = :id")
                    .setParameter("createdAt", LocalDateTime.of(2026, 3, 5, 23, 59, 59))
                    .setParameter("id", placed.getId()).executeUpdate();
            return null;
        });
        assertEquals(1, orders.getCustomerOrders(f.customerId(), OrderStatus.PENDING_CONFIRMATION,
                LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, orders.getCustomerOrders(f.customerId(), null,
                LocalDate.of(2026, 3, 6), null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, orders.getCustomerOrders(-1L, null,
                null, LocalDate.of(9999, 12, 31), PageRequest.of(0, 10)).getTotalElements());
        assertThrows(IllegalArgumentException.class, () -> orders.getCustomerOrders(f.customerId(), null,
                LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 5), PageRequest.of(0, 10)));
    }

    @Test
    void concurrentCheckoutCannotPurchaseTheSameSelectedLinesTwice() throws Exception {
        Fixture f = fixture(true, false, false);
        List<Boolean> results = runConcurrently(
                () -> tryCheckout(f.customerId()), () -> tryCheckout(f.customerId()));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, orders.getCustomerOrders(f.customerId(), null, PageRequest.of(0, 10)).getTotalElements());
        inTransaction(() -> {
            assertEquals(8, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(2, cartItems.findByCartId(f.cartId()).size());
            return null;
        });
    }

    @Test
    void concurrentCancelAndStaffConfirmationHaveOnlyOneWinner() throws Exception {
        Long staffId = staffActor();
        Fixture f = fixture(true, false, false);
        Order placed = orders.checkoutOrder(f.customerId(), request(PaymentMethod.COD));
        List<Boolean> results = runConcurrently(() -> {
            try { orders.customerCancelOrder(placed.getId(), f.customerId(), "Hủy"); return true; }
            catch (IllegalStateException expected) { return false; }
        }, () -> {
            try { orders.confirmOrder(placed.getId(), staffId); return true; }
            catch (IllegalStateException expected) { return false; }
        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        inTransaction(() -> {
            Order result = orderRepository.findById(placed.getId()).orElseThrow();
            assertEquals(2, result.getStatusHistories().size());
            int expectedStock = result.getOrderStatus() == OrderStatus.CANCELLED ? 10 : 8;
            assertEquals(expectedStock, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            return null;
        });
    }

    @Test
    void concurrentCancellationsRestoreStockAndVoucherExactlyOnce() throws Exception {
        Fixture f = fixture(true, false, false);
        Long voucherId = voucher();
        CheckoutSubmitDto request = request(PaymentMethod.COD);
        request.setVoucherCode(inTransaction(() -> vouchers.findById(voucherId).orElseThrow().getCode()));
        Order placed = orders.checkoutOrder(f.customerId(), request);
        Supplier<Boolean> cancel = () -> {
            try { orders.customerCancelOrder(placed.getId(), f.customerId(), "Đồng thời"); return true; }
            catch (IllegalStateException expected) { return false; }
        };
        assertEquals(1, runConcurrently(cancel, cancel).stream().filter(Boolean::booleanValue).count());
        inTransaction(() -> {
            assertEquals(10, variants.findById(f.firstId()).orElseThrow().getStockQuantity());
            assertEquals(0, vouchers.findById(voucherId).orElseThrow().getUsedCount());
            assertEquals(2, orderRepository.findById(placed.getId()).orElseThrow().getStatusHistories().size());
            return null;
        });
    }

    private boolean tryCheckout(Long customerId) {
        try { orders.checkoutOrder(customerId, request(PaymentMethod.COD)); return true; }
        catch (IllegalArgumentException expected) { return false; }
    }

    private List<Boolean> runConcurrently(Supplier<Boolean> first, Supplier<Boolean> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> one = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return first.get(); };
            Callable<Boolean> two = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return second.get(); };
            Future<Boolean> result1 = executor.submit(one);
            Future<Boolean> result2 = executor.submit(two);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(result1.get(20, TimeUnit.SECONDS), result2.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private CheckoutSubmitDto request(PaymentMethod method) {
        return CheckoutSubmitDto.builder().recipientName("Người nhận").recipientPhone("0901234567")
                .shippingProvince("Hà Nội").shippingDistrict("Cầu Giấy").shippingWard("Dịch Vọng")
                .shippingAddressLine("123 Đường A").paymentMethod(method).build();
    }

    private Long voucher() {
        return inTransaction(() -> vouchers.save(Voucher.builder().code("V-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT))
                .title("Test 10%").discountType(DiscountType.PERCENT).discountValue(BigDecimal.TEN)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
                .totalUsageLimit(100).perCustomerLimit(1).build()).getId());
    }

    private Fixture fixture(boolean firstSelected, boolean secondSelected, boolean thirdSelected) {
        return inTransaction(() -> {
            String suffix = UUID.randomUUID().toString();
            Role role = roles.findByCode("CUSTOMER").orElseGet(() -> roles.save(Role.builder().code("CUSTOMER").name("Customer").build()));
            User user = users.save(User.builder().role(role).fullName("Customer")
                    .email(suffix + "@test.invalid").passwordHash("unused").build());
            Category category = categories.save(Category.builder().code(suffix).name("Test").build());
            Product product = products.save(Product.builder().category(category).name("Product snapshot")
                    .slug(suffix).createdBy(user).build());
            Cart cart = carts.save(Cart.builder().user(user).build());
            Long[] ids = new Long[3];
            boolean[] selected = {firstSelected, secondSelected, thirdSelected};
            for (int i = 0; i < 3; i++) {
                ProductVariant variant = variants.save(ProductVariant.builder().product(product)
                        .skuCode(suffix + "-" + i).variantName("SKU " + (i + 1))
                        .price(new BigDecimal("100000").multiply(BigDecimal.valueOf(i + 1))).stockQuantity(10).build());
                ids[i] = variant.getId();
                cartItems.save(CartItem.builder().cart(cart).variant(variant).quantity(2).isSelected(selected[i]).build());
            }
            return new Fixture(user.getId(), cart.getId(), ids[0], ids[1], ids[2]);
        });
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private record Fixture(Long customerId, Long cartId, Long firstId, Long secondId, Long thirdId) { }
    private Long staffActor() {
        return inTransaction(() -> {
            var role=roles.findByCode("STAFF").orElseGet(()->roles.save(Role.builder().code("STAFF").name("Staff").build()));
            return users.save(User.builder().role(role).fullName("Order operator").email(UUID.randomUUID()+"@staff.test")
                    .passwordHash("unused").status("ACTIVE").build()).getId();
        });
    }
}
