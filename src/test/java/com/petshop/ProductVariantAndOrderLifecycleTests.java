package com.petshop;

import com.petshop.dto.catalog.ProductVariantFormDto;
import com.petshop.dto.order.BuyAgainResultDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.CartService;
import com.petshop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vnpay.tmn-code=", "vnpay.hash-secret=", "vnpay.return-url=",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.datasource.url=jdbc:h2:mem:variantOrderTest;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
})
@ActiveProfiles("test")
@Transactional
class ProductVariantAndOrderLifecycleTests {

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;

    private User customer;
    private User staff;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        Role custRole = roles.findByCode("CUSTOMER").orElseGet(() -> roles.save(Role.builder().code("CUSTOMER").name("Khách hàng").build()));
        Role staffRole = roles.findByCode("STAFF").orElseGet(() -> roles.save(Role.builder().code("STAFF").name("Nhân viên").build()));

        customer = users.save(User.builder().role(custRole).fullName("Khách Mua Lại " + key)
                .email(key + "@buyagain.test").passwordHash("hashed").status("ACTIVE").build());
        staff = users.save(User.builder().role(staffRole).fullName("Nhân Viên Giao " + key)
                .email(key + "@staff.test").passwordHash("hashed").status("ACTIVE").build());

        Category cat = categories.save(Category.builder().code("CAT-" + key).name("Danh mục " + key).isActive(true).build());
        product = products.save(Product.builder().name("Thức ăn Poodle " + key).slug("poodle-" + key)
                .category(cat).createdBy(customer).status(ProductStatus.ACTIVE).petType(PetType.DOG).build());

        variant = variants.save(ProductVariant.builder()
                .product(product)
                .skuCode("SKU-" + key)
                .variantName("Vị Gà")
                .color("Đỏ")
                .weightKg(new BigDecimal("1.50"))
                .price(new BigDecimal("180000"))
                .stockQuantity(10)
                .status(ProductVariantStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("3.1. Thuộc tính Color và WeightKg được lưu trữ và chuyển đổi chính xác trong Entity và DTO")
    void variantAttributesColorAndWeightArePreserved() {
        ProductVariant found = variants.findById(variant.getId()).orElseThrow();
        assertThat(found.getColor()).isEqualTo("Đỏ");
        assertThat(found.getWeightKg()).isEqualByComparingTo("1.50");

        ProductVariantFormDto dto = ProductVariantFormDto.fromEntity(found);
        assertThat(dto.getColor()).isEqualTo("Đỏ");
        assertThat(dto.getWeightKg()).isEqualByComparingTo("1.50");

        dto.setColor("Xanh");
        dto.setWeightKg(new BigDecimal("2.50"));
        ProductVariant mapped = dto.toEntity();
        assertThat(mapped.getColor()).isEqualTo("Xanh");
        assertThat(mapped.getWeightKg()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("5.1. Luồng giao hàng thất bại ghi nhận số lần giao, cho phép giao lại, và tự động hoàn kho sau 3 lần")
    void deliveryFailureFlowAndAutoReturn() {
        Order order = orders.save(Order.builder()
                .orderCode("ORD-" + UUID.randomUUID().toString().substring(0, 18))
                .customer(customer)
                .recipientName("Người nhận")
                .recipientPhone("0901234567")
                .shippingProvince("Hà Nội")
                .shippingDistrict("Cầu Giấy")
                .shippingWard("Dịch Vọng")
                .shippingAddressLine("Số 1 đường A")
                .subtotalAmount(new BigDecimal("180000"))
                .shippingFee(new BigDecimal("30000"))
                .totalAmount(new BigDecimal("210000"))
                .orderStatus(OrderStatus.PREPARING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .stockDeducted(true)
                .build());

        OrderItem item = orderItems.save(OrderItem.builder()
                .order(order)
                .variant(variant)
                .productNameSnapshot(product.getName())
                .skuCodeSnapshot(variant.getSkuCode())
                .variantNameSnapshot(variant.getVariantName())
                .unitPrice(variant.getPrice())
                .quantity(2)
                .build());
        order.getItems().add(item);

        // 1. Chuyển sang SHIPPING
        orderService.markShipping(order.getId(), staff.getId());
        Order updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);

        // 2. Giao thất bại lần 1
        orderService.recordDeliveryFailure(order.getId(), staff.getId(), "Khách không bắt máy");
        updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getOrderStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(updated.getDeliveryAttempts()).isEqualTo(1);
        assertThat(updated.getFailedDeliveryReason()).isEqualTo("Khách không bắt máy");

        // 3. Giao lại lần 2
        orderService.markShipping(order.getId(), staff.getId());
        updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);

        // 4. Giao thất bại lần 2
        orderService.recordDeliveryFailure(order.getId(), staff.getId(), "Khách hẹn hôm khác");
        updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getOrderStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(updated.getDeliveryAttempts()).isEqualTo(2);

        // 5. Giao lại lần 3
        orderService.markShipping(order.getId(), staff.getId());

        // 6. Giao thất bại lần 3 -> Đạt MAX (3 lần) -> Tự động chuyển sang RETURNED
        int initialStock = variant.getStockQuantity(); // 10
        orderService.recordDeliveryFailure(order.getId(), staff.getId(), "Khách từ chối nhận hàng hoàn toàn");
        updated = orders.findById(order.getId()).orElseThrow();
        assertThat(updated.getOrderStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(updated.getDeliveryAttempts()).isEqualTo(3);
        assertFalse(updated.getStockDeducted());

        // Kiểm tra số lượng tồn kho đã được hoàn lại đúng (+2)
        ProductVariant refreshedVariant = variants.findById(variant.getId()).orElseThrow();
        assertThat(refreshedVariant.getStockQuantity()).isEqualTo(initialStock + 2);
    }

    @Test
    @DisplayName("5.2. Chức năng Buy Again thêm đúng biến thể đã mua vào giỏ hàng với số lượng mặc định là 1")
    void buyAgainAddsPurchasedVariantsWithDefaultQuantityOne() {
        Order order = orders.save(Order.builder()
                .orderCode("BUY-" + UUID.randomUUID().toString().substring(0, 18))
                .customer(customer)
                .recipientName("Người nhận")
                .recipientPhone("0901234567")
                .shippingProvince("Hà Nội")
                .shippingDistrict("Cầu Giấy")
                .shippingWard("Dịch Vọng")
                .shippingAddressLine("Số 1 đường A")
                .subtotalAmount(new BigDecimal("180000"))
                .shippingFee(new BigDecimal("30000"))
                .totalAmount(new BigDecimal("210000"))
                .orderStatus(OrderStatus.COMPLETED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .stockDeducted(true)
                .build());

        OrderItem item = orderItems.save(OrderItem.builder()
                .order(order)
                .variant(variant)
                .productNameSnapshot(product.getName())
                .skuCodeSnapshot(variant.getSkuCode())
                .variantNameSnapshot(variant.getVariantName())
                .unitPrice(variant.getPrice())
                .quantity(5) // Đơn trước mua số lượng 5
                .build());
        order.getItems().add(item);

        BuyAgainResultDto result = orderService.buyAgain(order.getId(), customer.getId());
        assertThat(result.getAddedCount()).isEqualTo(1);
        assertThat(result.getUnavailableItems()).isEmpty();

        // Kiểm tra trong giỏ hàng: số lượng được thêm mặc định là 1
        var cart = cartService.getCartForUser(customer.getId());
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getVariantId()).isEqualTo(variant.getId());
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(1); // Mặc định là 1 chứ không phải 5
    }
}
