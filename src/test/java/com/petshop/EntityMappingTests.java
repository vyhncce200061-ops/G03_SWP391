package com.petshop;

import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityMappingTests {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockImportRepository stockImportRepository;

    @Test
    @DisplayName("Should successfully persist Role, User, and verify relationships")
    void testUserAndRolePersistence() {
        Role role = Role.builder()
                .code("CUSTOMER")
                .name("Khách Hàng")
                .build();
        role = roleRepository.save(role);
        assertNotNull(role.getId());

        User user = User.builder()
                .role(role)
                .fullName("Test Customer")
                .email("customer@test.com")
                .phone("0987654321")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .status("ACTIVE")
                .build();
        user = userRepository.save(user);
        assertNotNull(user.getId());

        User found = userRepository.findByEmail("customer@test.com").orElse(null);
        assertNotNull(found);
        assertEquals("Test Customer", found.getFullName());
        assertEquals("CUSTOMER", found.getRole().getCode());
    }

    @Test
    @DisplayName("Should successfully persist Catalog: Category, Brand, Product and ProductVariant")
    void testCatalogEntities() {
        Role adminRole = roleRepository.save(Role.builder()
                .code("ADMIN")
                .name("Quản Trị Viên")
                .build());

        User creator = userRepository.save(User.builder()
                .role(adminRole)
                .fullName("Catalog Admin")
                .email("admin-catalog@test.com")
                .phone("0900000001")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .status("ACTIVE")
                .build());

        Category cat = Category.builder()
                .code("DOG_CAT")
                .name("Chó Cảnh")
                .sortOrder(1)
                .isActive(true)
                .build();
        cat = categoryRepository.save(cat);

        Brand brand = Brand.builder()
                .name("Royal Canin")
                .description("Thức ăn dinh dưỡng chó mèo")
                .isActive(true)
                .build();
        brand = brandRepository.save(brand);

        Product product = Product.builder()
                .category(cat)
                .brand(brand)
                .slug("royal-canin-adult-dog")
                .name("Royal Canin Adult Dog")
                .petType(PetType.DOG)
                .status(ProductStatus.ACTIVE)
                .createdBy(creator)
                .build();
        product = productRepository.save(product);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .skuCode("SKU-RC-01")
                .variantName("Bao 3kg")
                .price(BigDecimal.valueOf(350000))
                .originalPrice(BigDecimal.valueOf(380000))
                .stockQuantity(50)
                .lowStockThreshold(5)
                .status(ProductVariantStatus.ACTIVE)
                .build();
        variant = productVariantRepository.save(variant);

        assertNotNull(variant.getId());
        assertEquals(1, productVariantRepository.findByProductIdAndStatus(product.getId(), ProductVariantStatus.ACTIVE).size());
    }

    @Test
    @DisplayName("Should successfully persist Cart, CartItem, and composite key WishlistItem")
    void testCartAndWishlistPersistence() {
        Role role = roleRepository.save(Role.builder().code("CUSTOMER").name("Khách Hàng").build());
        User user = userRepository.save(User.builder()
                .role(role)
                .fullName("Cart Tester")
                .email("cart@test.com")
                .phone("0912345678")
                .passwordHash("hashed")
                .status("ACTIVE")
                .build());

        Category cat = categoryRepository.save(Category.builder().code("CAT").name("Mèo").build());
        Product prod = productRepository.save(Product.builder()
                .category(cat)
                .slug("pate-meo")
                .name("Pate Mèo")
                .petType(PetType.CAT)
                .status(ProductStatus.ACTIVE)
                .createdBy(user)
                .build());
        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .skuCode("SKU-PATE-01")
                .variantName("Lon 85g")
                .price(BigDecimal.valueOf(25000))
                .stockQuantity(100)
                .status(ProductVariantStatus.ACTIVE)
                .build());

        Cart cart = cartRepository.save(Cart.builder().user(user).build());
        CartItem cartItem = cartItemRepository.save(CartItem.builder()
                .cart(cart)
                .variant(variant)
                .quantity(3)
                .build());
        assertNotNull(cartItem.getId());

        // Test Composite Key WishlistItem (CustomerId, ProductId)
        WishlistItemId wishId = new WishlistItemId(user.getId(), prod.getId());
        WishlistItem wishlistItem = WishlistItem.builder()
                .id(wishId)
                .customer(user)
                .product(prod)
                .build();
        wishlistItem = wishlistItemRepository.save(wishlistItem);

        assertNotNull(wishlistItem.getId());
        assertEquals(user.getId(), wishlistItem.getId().getCustomerId());
        assertEquals(prod.getId(), wishlistItem.getId().getProductId());
    }

    @Test
    @DisplayName("Should successfully persist Voucher, Order and OrderItem")
    void testOrderAndVoucherPersistence() {
        Role role = roleRepository.save(Role.builder().code("CUSTOMER").name("Khách Hàng").build());
        User user = userRepository.save(User.builder()
                .role(role)
                .fullName("Order Tester")
                .email("order@test.com")
                .phone("0988776655")
                .passwordHash("hashed")
                .status("ACTIVE")
                .build());

        Voucher voucher = voucherRepository.save(Voucher.builder()
                .code("DISCOUNT10")
                .title("Giảm 10%")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .minOrderAmount(BigDecimal.valueOf(100000))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(10))
                .totalUsageLimit(100)
                .status(VoucherStatus.ACTIVE)
                .build());

        Category cat = categoryRepository.save(Category.builder().code("ACC").name("Phụ Kiện").build());
        Product prod = productRepository.save(Product.builder()
                .category(cat)
                .slug("day-dat-cho")
                .name("Dây Dắt Chó")
                .petType(PetType.DOG)
                .status(ProductStatus.ACTIVE)
                .createdBy(user)
                .build());
        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .skuCode("SKU-DAY-01")
                .variantName("Đỏ")
                .price(BigDecimal.valueOf(150000))
                .stockQuantity(20)
                .status(ProductVariantStatus.ACTIVE)
                .build());

        Order order = Order.builder()
                .orderCode("ORD-2026-TEST")
                .customer(user)
                .voucher(voucher)
                .orderStatus(OrderStatus.PENDING_CONFIRMATION)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .subtotalAmount(BigDecimal.valueOf(150000))
                .discountAmount(BigDecimal.valueOf(15000))
                .shippingFee(BigDecimal.valueOf(30000))
                .totalAmount(BigDecimal.valueOf(165000))
                .recipientName("Order Tester")
                .recipientPhone("0988776655")
                .shippingProvince("Hà Nội")
                .shippingDistrict("Cầu Giấy")
                .shippingWard("Dịch Vọng")
                .shippingAddressLine("Số 123 Đường Cầu Giấy")
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .variant(variant)
                .productNameSnapshot(variant.getProduct().getName())
                .skuCodeSnapshot(variant.getSkuCode())
                .variantNameSnapshot(variant.getVariantName())
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(150000))
                .build();
        order.getItems().add(item);

        order = orderRepository.save(order);
        assertNotNull(order.getId());
        assertEquals(1, order.getItems().size());
        assertEquals("ORD-2026-TEST", order.getOrderCode());
    }

    @Test
    @DisplayName("Should successfully persist StockImport and StockImportItem")
    void testStockImportPersistence() {
        Role role = roleRepository.save(Role.builder().code("STAFF").name("Nhân Viên").build());
        User staff = userRepository.save(User.builder()
                .role(role)
                .fullName("Staff Member")
                .email("staff@test.com")
                .phone("0933221100")
                .passwordHash("hashed")
                .status("ACTIVE")
                .build());

        Category cat = categoryRepository.save(Category.builder().code("IMP_CAT").name("Hàng Nhập").build());
        Product prod = productRepository.save(Product.builder()
                .category(cat)
                .slug("thuc-an-nhap-khau")
                .name("Thức Ăn Nhập Khẩu")
                .petType(PetType.DOG)
                .status(ProductStatus.ACTIVE)
                .createdBy(staff)
                .build());
        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .skuCode("SKU-IMP-01")
                .variantName("Gói 1kg")
                .price(BigDecimal.valueOf(120000))
                .stockQuantity(10)
                .status(ProductVariantStatus.ACTIVE)
                .build());

        StockImport stockImport = StockImport.builder()
                .importCode("IMP-2026-001")
                .createdBy(staff)
                .supplierName("Pet Global Supply")
                .note("Nhập lô hàng ban đầu")
                .importedAt(LocalDateTime.now())
                .build();

        StockImportItem importItem = StockImportItem.builder()
                .stockImport(stockImport)
                .variant(variant)
                .quantity(10)
                .unitCost(BigDecimal.valueOf(80000))
                .build();
        stockImport.getItems().add(importItem);

        stockImport = stockImportRepository.save(stockImport);
        assertNotNull(stockImport.getId());
        assertEquals(1, stockImport.getItems().size());
    }
}
