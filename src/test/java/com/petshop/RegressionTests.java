package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.*;
import com.petshop.dto.admin.StaffCreateRequestDto;
import com.petshop.dto.catalog.ProductFilterDto;
import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.dto.voucher.VoucherRequestDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegressionTests {
    @org.springframework.boot.test.mock.mockito.MockBean EmailService emailService;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired VoucherRepository vouchers;
    @Autowired CustomerVoucherRepository wallet;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired ReviewRepository reviews;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired CartRepository cartRepository;
    @Autowired UserAddressRepository addresses;
    @Autowired UserService userService;
    @Autowired VoucherService voucherService;
    @Autowired OrderService orderService;
    @Autowired CheckoutPlacementService placementService;
    @Autowired CartService carts;
    @Autowired CatalogService catalog;
    @Autowired StockImportService imports;
    @Autowired ReportService reports;
    @Autowired AuthService auth;
    @Autowired Validator validator;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    User customer,admin,staff;
    Product product;
    ProductVariant variant;
    Category category;

    @BeforeEach void fixture() {
        var cr=roles.save(Role.builder().code("CUSTOMER").name("Customer").build());
        var ar=roles.save(Role.builder().code("ADMIN").name("Admin").build());
        var sr=roles.save(Role.builder().code("STAFF").name("Staff").build());
        String hash=encoder.encode("Secret@123");
        customer=users.save(User.builder().role(cr).fullName("Customer").email("c@regression.test").phone("0901000001").passwordHash(hash).build());
        admin=users.save(User.builder().role(ar).fullName("Admin").email("a@regression.test").phone("0901000002").passwordHash(hash).build());
        staff=users.save(User.builder().role(sr).fullName("Staff").email("s@regression.test").phone("0901000003").passwordHash(hash).build());
        category=categories.save(Category.builder().code("FOOD").name("Food").build());
        product=products.save(Product.builder().category(category).createdBy(admin).name("Dog food").slug("regression-dog-food").petType(PetType.DOG).status(ProductStatus.ACTIVE).build());
        variant=variants.save(ProductVariant.builder().product(product).skuCode("REG-FOOD").variantName("Small").price(new BigDecimal("200000")).stockQuantity(10).status(ProductVariantStatus.ACTIVE).build());
        em.flush();
    }
    VoucherRequestDto voucherForm() {
        return VoucherRequestDto.builder().code("REG10").title("Regression voucher").discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.TEN).minOrderAmount(BigDecimal.ZERO).maxDiscountAmount(new BigDecimal("50000"))
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(2)).totalUsageLimit(10).perCustomerLimit(2).build();
    }
    CheckoutSubmitDto checkout() {
        return CheckoutSubmitDto.builder().recipientName("Customer").recipientPhone("0901000001")
                .shippingProvince("HCM").shippingDistrict("Thu Duc").shippingWard("Linh Trung").shippingAddressLine("123 Road")
                .paymentMethod(PaymentMethod.COD).build();
    }
    @Test void staffInvitationRequiresEmailAndSendsSetupLink() {
        long count=users.count();
        var form=StaffCreateRequestDto.builder().fullName("New staff").email("").phone("0901000004").build();
        assertThrows(IllegalArgumentException.class,()->userService.createStaffAccount(form));
        assertEquals(count,users.count());
        form.setEmail("new@regression.test");
        var created = userService.createStaffAccount(form);
        assertEquals("STAFF",created.getRole().getCode());
        assertTrue(created.getMustChangePassword());
        verify(emailService).sendStaffInvitation(eq(form.getEmail()), eq("New staff"), contains("/reset-password?token="));
        assertEquals(1, tokens.count());
    }

    @Test void baoRegistrationRequiresEmailConfirmationThenCreatesCustomerAndCart() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("fullName","New customer").param("email","new.confirm@regression.test")
                .param("phone","0901000021").param("password","Customer@123").param("confirmPassword","Customer@123"))
                .andExpect(redirectedUrl("/login")).andExpect(flash().attributeExists("successMessage"));
        assertFalse(users.existsByEmail("new.confirm@regression.test"));
        var link=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendRegistrationConfirmation(eq("new.confirm@regression.test"),eq("New customer"),link.capture());
        var uri=java.net.URI.create(link.getValue());String token=uri.getQuery().substring(6);
        assertEquals("/verify-email",uri.getPath());assertFalse(auth.verifyPasswordResetToken(token));
        mvc.perform(get("/verify-email").param("token",token))
                .andExpect(redirectedUrl("/login")).andExpect(flash().attributeExists("successMessage"));
        var created=users.findByEmail("new.confirm@regression.test").orElseThrow();
        assertEquals("CUSTOMER",created.getRole().getCode());assertTrue(encoder.matches("Customer@123",created.getPasswordHash()));
        assertTrue(cartRepository.findByUserId(created.getId()).isPresent());
        mvc.perform(get("/verify-email").param("token",token))
                .andExpect(redirectedUrl("/login")).andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/login").with(csrf()).param("username",created.getEmail()).param("password","Customer@123"))
                .andExpect(redirectedUrl("/"));
    }

    @Test void registrationRejectsTamperingAndSmtpFailureWithoutCreatingAccounts() throws Exception {
        mvc.perform(get("/verify-email").param("token","invalid-token"))
                .andExpect(redirectedUrl("/login")).andExpect(flash().attributeExists("errorMessage"));
        doThrow(new IllegalStateException("SMTP unavailable")).when(emailService).sendRegistrationConfirmation(anyString(),anyString(),anyString());
        long before=users.count();
        mvc.perform(post("/register").with(csrf()).param("fullName","SMTP failure").param("email","mail.failure@regression.test")
                .param("phone","0901000022").param("password","Customer@123").param("confirmPassword","Customer@123"))
                .andExpect(status().isOk()).andExpect(view().name("guest/register")).andExpect(model().attributeExists("errorMessage"));
        assertEquals(before,users.count());
        mvc.perform(get("/login").param("error","true")).andExpect(status().isOk());
        mvc.perform(get("/reset-password").param("token","invalid-token")).andExpect(status().isOk());
    }

    @Test void baoCustomerPagesRenderWithoutInventingDemoOrdersOrAddresses() throws Exception {
        long beforeOrders=orders.count(),beforeAddresses=addresses.count(),beforeUsers=users.count();
        for(String path:List.of("/customer/profile","/customer/addresses","/customer/orders","/cart")) {
            mvc.perform(get(path).with(user(new CustomUserDetails(customer)))).andExpect(status().isOk());
        }
        assertEquals(beforeOrders,orders.count());assertEquals(beforeAddresses,addresses.count());assertEquals(beforeUsers,users.count());
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        mvc.perform(get("/cart").with(user(new CustomUserDetails(customer)))).andExpect(status().isOk());
        var order=orderService.checkoutOrder(customer.getId(),checkout());
        for(String path:List.of("/customer/profile?tab=orders","/customer/orders","/customer/orders/"+order.getId())) {
            mvc.perform(get(path).with(user(new CustomUserDetails(customer)))).andExpect(status().isOk());
        }
    }

    @Test void baoAjaxCartUpdateRetainsCsrfAndOwnershipChecks() throws Exception {
        var cart=carts.addItemToCart(customer.getId(),variant.getId(),1);Long itemId=cart.getItems().get(0).getId();
        mvc.perform(post("/cart/update").with(user(new CustomUserDetails(customer))).with(csrf()).header("X-Requested-With","XMLHttpRequest")
                .param("itemId",itemId.toString()).param("quantity","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(2));
        mvc.perform(post("/cart/update").with(user(new CustomUserDetails(customer))).header("X-Requested-With","XMLHttpRequest")
                .param("itemId",itemId.toString()).param("quantity","3"))
                .andExpect(redirectedUrl("/error/403"));
        var other=users.save(User.builder().role(customer.getRole()).fullName("Other customer").email("other@regression.test").phone("0901000023")
                .passwordHash(encoder.encode("Other@123")).build());
        mvc.perform(post("/cart/update").with(user(new CustomUserDetails(other))).with(csrf()).header("X-Requested-With","XMLHttpRequest")
                .param("itemId",itemId.toString()).param("quantity","3"))
                .andExpect(status().isBadRequest());
        assertEquals(2,carts.getCartForUser(customer.getId()).getTotalQuantity());
    }

    @Test void customerOrderPageItemsRemainReadableAfterDetachingEntities() {
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        var placed=orderService.checkoutOrder(customer.getId(),checkout());
        em.flush();em.clear();
        var page=orderService.getCustomerOrders(customer.getId(),null,PageRequest.of(0,1));
        em.clear();
        assertEquals(1,page.getTotalElements());assertEquals(placed.getId(),page.getContent().get(0).getId());
        assertEquals(1,page.getContent().get(0).getItems().size());
        assertEquals("Dog food",page.getContent().get(0).getItems().get(0).getProductNameSnapshot());
    }

    @Test void avatarUploadRejectsDisguisedFilesAndStoresValidImage() throws Exception {
        var bogus=new org.springframework.mock.web.MockMultipartFile("avatarFile","fake.png","image/png","not an image".getBytes());
        assertThrows(IllegalArgumentException.class,()->userService.updateAvatar(customer.getId(),bogus));
        assertNull(customer.getAvatarUrl());
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        var valid=new org.springframework.mock.web.MockMultipartFile("avatarFile","../../unsafe.html","image/png",bytes.toByteArray());
        userService.updateAvatar(customer.getId(),valid);
        var saved=users.findById(customer.getId()).orElseThrow();
        assertTrue(saved.getAvatarUrl().matches("/uploads/avatars/avatar_[0-9]+_[a-f0-9-]+\\.png"));
        var file=java.nio.file.Path.of(saved.getAvatarUrl().substring(1));
        try { assertNotNull(javax.imageio.ImageIO.read(file.toFile())); }
        finally { java.nio.file.Files.deleteIfExists(file); }
    }
    @Test void voucherDatesMustBeStrictlyIncreasing() {
        var form=voucherForm();form.setEndDate(form.getStartDate());
        assertThrows(IllegalArgumentException.class,()->voucherService.createVoucher(form));
        assertEquals(0,vouchers.count());
    }
    @Test void zeroDiscountCapRejectedAtDtoAndService() {
        var form=voucherForm();form.setMaxDiscountAmount(BigDecimal.ZERO);
        assertFalse(validator.validate(form).isEmpty());
        assertThrows(IllegalArgumentException.class,()->voucherService.createVoucher(form));
        form.setMaxDiscountAmount(null);assertTrue(validator.validate(form).isEmpty());
        assertNotNull(voucherService.createVoucher(form).getId());
    }
    @Test void voucherQuotaCannotBeReducedBelowUsedCount() {
        Voucher v=voucherService.createVoucher(voucherForm());v.setUsedCount(5);vouchers.saveAndFlush(v);
        var form=voucherForm();form.setTotalUsageLimit(3);
        assertThrows(IllegalArgumentException.class,()->voucherService.updateVoucher(v.getId(),form));
        em.clear();assertEquals(10,vouchers.findById(v.getId()).orElseThrow().getTotalUsageLimit());
    }
    @Test void activeLastAdminCannotBeLocked() {
        assertThrows(IllegalArgumentException.class,()->userService.toggleUserStatus(admin.getId(),staff.getId()));
        assertEquals("ACTIVE",users.findById(admin.getId()).orElseThrow().getStatus());
    }
    @Test void existingSessionIsRevokedWhenAccountLockedOrRoleChanged() throws Exception {
        var old=new CustomUserDetails(customer);customer.setStatus("LOCKED");users.saveAndFlush(customer);
        mvc.perform(get("/account/profile").with(user(old))).andExpect(redirectedUrl("/login?expired=true"));
        customer.setStatus("ACTIVE");customer.setRole(staff.getRole());users.saveAndFlush(customer);
        mvc.perform(get("/account/profile").with(user(old))).andExpect(redirectedUrl("/login?expired=true"));
    }
    @Test void staffRecoveryKeepsCurrentLoginUntilStaffConfirmsNewPassword() throws Exception {
        var old=new CustomUserDetails(staff);
        String originalHash=staff.getPasswordHash(), customerHash=customer.getPasswordHash();
        mvc.perform(post("/admin/users/"+staff.getId()+"/reset-password").with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("successMessage"));
        assertEquals(originalHash,users.findById(staff.getId()).orElseThrow().getPasswordHash());
        assertFalse(users.findById(staff.getId()).orElseThrow().getMustChangePassword());
        mvc.perform(get("/account/profile").with(user(old))).andExpect(status().isOk());
        mvc.perform(post("/login").with(csrf()).param("username",staff.getEmail()).param("password","Secret@123"))
                .andExpect(redirectedUrl("/staff/dashboard"));
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(staff.getEmail()), anyString(), link.capture());
        String token = java.net.URI.create(link.getValue()).getQuery().substring("token=".length());
        mvc.perform(post("/reset-password").with(csrf()).param("token",token).param("newPassword","Changed@123").param("confirmPassword","Changed@123"))
                .andExpect(redirectedUrl("/login"));
        assertFalse(users.findById(staff.getId()).orElseThrow().getMustChangePassword());
        assertTrue(encoder.matches("Changed@123",users.findById(staff.getId()).orElseThrow().getPasswordHash()));
        assertFalse(encoder.matches("Secret@123",users.findById(staff.getId()).orElseThrow().getPasswordHash()));
        mvc.perform(get("/account/profile").with(user(old))).andExpect(redirectedUrl("/login?expired=true"));
        assertEquals(customerHash,users.findById(customer.getId()).orElseThrow().getPasswordHash());
        assertFalse(auth.verifyPasswordResetToken(token));
    }

    @Test void adminPasswordSupportRejectsCustomersAdminsAndIneligibleStaff() throws Exception {
        String customerHash=customer.getPasswordHash(), adminHash=admin.getPasswordHash(), staffHash=staff.getPasswordHash();
        for(var target:List.of(customer,admin)) {
            assertThrows(IllegalArgumentException.class,()->userService.requestStaffPasswordReset(target.getId(),admin.getId()));
            mvc.perform(post("/admin/users/"+target.getId()+"/reset-password").with(user(new CustomUserDetails(admin))).with(csrf()))
                    .andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("errorMessage"));
        }
        staff.setStatus("LOCKED");users.saveAndFlush(staff);
        assertThrows(IllegalArgumentException.class,()->userService.requestStaffPasswordReset(staff.getId(),admin.getId()));
        mvc.perform(post("/admin/users/"+staff.getId()+"/reset-password").with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(flash().attributeExists("errorMessage"));
        staff.setStatus("ACTIVE");staff.setMustChangePassword(true);users.saveAndFlush(staff);
        assertThrows(IllegalArgumentException.class,()->userService.requestStaffPasswordReset(staff.getId(),admin.getId()));
        mvc.perform(post("/admin/users/"+staff.getId()+"/reset-password").with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(0,tokens.count());verifyNoInteractions(emailService);
        assertEquals(customerHash,customer.getPasswordHash());assertEquals(adminHash,admin.getPasswordHash());assertEquals(staffHash,staff.getPasswordHash());
    }

    @Test void onlyActiveAdminCanRequestStaffPasswordSupport() throws Exception {
        for(var actor:List.of(customer,staff)) {
            assertThrows(IllegalArgumentException.class,()->userService.requestStaffPasswordReset(staff.getId(),actor.getId()));
            mvc.perform(post("/admin/users/"+staff.getId()+"/reset-password").with(user(new CustomUserDetails(actor))).with(csrf()))
                    .andExpect(redirectedUrl("/error/403"));
        }
        admin.setStatus("LOCKED");users.saveAndFlush(admin);
        assertThrows(IllegalArgumentException.class,()->userService.requestStaffPasswordReset(staff.getId(),admin.getId()));
        assertEquals(0,tokens.count());verifyNoInteractions(emailService);
    }

    @Test void userManagementTabsAndActionsMatchAccountTypeAndState() throws Exception {
        String html=mvc.perform(get("/admin/users").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("Hỗ trợ tài khoản:"));
        assertTrue(html.contains("/admin/users/"+staff.getId()+"/reset-password"));
        assertFalse(html.contains("/admin/users/"+customer.getId()+"/reset-password"));
        assertFalse(html.contains("/admin/users/"+admin.getId()+"/reset-password"));
        assertFalse(html.contains("/admin/users/"+staff.getId()+"/resend-invitation"));
        String filtered=mvc.perform(get("/admin/users").param("role","STAFF").param("keyword","Staff").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andExpect(model().attribute("role","STAFF")).andReturn().getResponse().getContentAsString();
        assertTrue(filtered.contains(staff.getEmail()));assertFalse(filtered.contains(customer.getEmail()));
        assertTrue(filtered.matches("(?s).*name=\"role\"[^>]*value=\"STAFF\".*"));
        staff.setMustChangePassword(true);users.saveAndFlush(staff);
        String pending=mvc.perform(get("/admin/users").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(pending.contains("/admin/users/"+staff.getId()+"/reset-password"));
        assertTrue(pending.contains("/admin/users/"+staff.getId()+"/resend-invitation"));
        staff.setStatus("LOCKED");users.saveAndFlush(staff);
        String locked=mvc.perform(get("/admin/users").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(locked.contains("/admin/users/"+staff.getId()+"/reset-password"));
        assertFalse(locked.contains("/admin/users/"+staff.getId()+"/resend-invitation"));
        String customers=mvc.perform(get("/admin/users").param("role","CUSTOMER").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(customers.contains("href=\"/admin/users/create\""));
    }

    @Test void staffCanReceiveEmailSetPasswordAndLoginWithoutAdminChoosingPassword() throws Exception {
        String html = mvc.perform(get("/admin/users/create").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("type=\"password\""));
        mvc.perform(post("/admin/users/create").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("fullName","Staff invited").param("email","invite@regression.test").param("phone","0901000005")
                .param("roleCode","ADMIN").param("password","Attacker@123"))
                .andExpect(redirectedUrl("/admin/users"));
        var created = users.findByEmail("invite@regression.test").orElseThrow();
        assertEquals("STAFF",created.getRole().getCode());
        assertFalse(encoder.matches("Attacker@123",created.getPasswordHash()));
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendStaffInvitation(eq(created.getEmail()),eq("Staff invited"),link.capture());
        String token = java.net.URI.create(link.getValue()).getQuery().substring(6);
        assertTrue(auth.verifyPasswordResetToken(token));
        assertNotEquals(token,tokens.findAll().get(0).getTokenHash());
        mvc.perform(post("/reset-password").with(csrf()).param("token",token).param("newPassword","StaffOwn@123").param("confirmPassword","StaffOwn@123"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin().user(created.getEmail()).password("StaffOwn@123"))
                .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated().withRoles("STAFF"));
        assertFalse(users.findById(created.getId()).orElseThrow().getMustChangePassword());
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(token,"Reuse@123"));
    }

    @Test void resendingStaffInvitationExpiresOldLinkAndRejectsNonPendingAccounts() throws Exception {
        var dto = StaffCreateRequestDto.builder().fullName("Staff invited").email("resend@regression.test").phone("0901000006").build();
        var created = userService.createStaffAccount(dto);
        userService.resendStaffInvitation(created.getId());
        var links = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService,times(2)).sendStaffInvitation(eq(created.getEmail()),anyString(),links.capture());
        String old = java.net.URI.create(links.getAllValues().get(0)).getQuery().substring(6);
        String latest = java.net.URI.create(links.getAllValues().get(1)).getQuery().substring(6);
        assertFalse(auth.verifyPasswordResetToken(old));
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(old,"Expired@123"));
        assertTrue(auth.verifyPasswordResetToken(latest));
        auth.resetPasswordWithToken(latest,"Chosen@123");
        assertThrows(IllegalArgumentException.class,()->userService.resendStaffInvitation(created.getId()));
        assertThrows(IllegalArgumentException.class,()->userService.resendStaffInvitation(customer.getId()));
    }

    @Test void unavailableMailShowsErrorAndDoesNotCreateStaff() throws Exception {
        doThrow(new IllegalStateException("SMTP chưa cấu hình")).when(emailService).requireConfigured();
        mvc.perform(post("/admin/users/create").with(user(new CustomUserDetails(admin))).with(csrf())
                .param("fullName","Staff invited").param("email","failure@regression.test").param("phone","0901000007"))
                .andExpect(view().name("admin/user-form")).andExpect(model().attributeExists("errorMessage"));
        assertFalse(users.existsByEmail("failure@regression.test"));
        assertEquals(0,tokens.count());
    }

    @Test void changingInvitedStaffEmailRevokesPreviouslySentLink() {
        var created=userService.createStaffAccount(StaffCreateRequestDto.builder().fullName("Staff pending")
                .email("previous@regression.test").phone("0901000010").build());
        var links=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendStaffInvitation(eq(created.getEmail()),anyString(),links.capture());
        String previous=java.net.URI.create(links.getValue()).getQuery().substring(6);
        userService.updateUserByAdmin(created.getId(),com.petshop.dto.admin.UserAdminUpdateDto.builder()
                .fullName(created.getFullName()).email("changed@regression.test").phone(created.getPhone()).status("ACTIVE").build(),admin.getId());
        assertFalse(auth.verifyPasswordResetToken(previous));
        userService.resendStaffInvitation(created.getId());
        verify(emailService).sendStaffInvitation(eq("changed@regression.test"),anyString(),contains("/reset-password?token="));
    }

    @Test void accountEditingCannotChangeRolesOrReassignRelatedData() throws Exception {
        carts.addItemToCart(customer.getId(),variant.getId(),1);
        var order=orderService.checkoutOrder(customer.getId(),checkout());
        product.setCreatedBy(staff);products.saveAndFlush(product);
        for (User target : List.of(customer,staff)) {
            mvc.perform(post("/admin/users/"+target.getId()+"/edit").with(user(new CustomUserDetails(admin))).with(csrf())
                    .param("fullName",target.getFullName()).param("email",target.getEmail()).param("phone",target.getPhone())
                    .param("status","ACTIVE").param("roleCode",target==customer?"STAFF":"CUSTOMER").param("roleId",admin.getRole().getId().toString()))
                    .andExpect(redirectedUrl("/admin/users"));
        }
        em.flush();em.clear();
        assertEquals("CUSTOMER",users.findById(customer.getId()).orElseThrow().getRole().getCode());
        assertEquals("STAFF",users.findById(staff.getId()).orElseThrow().getRole().getCode());
        assertEquals(customer.getId(),orders.findById(order.getId()).orElseThrow().getCustomer().getId());
        assertEquals(staff.getId(),products.findById(product.getId()).orElseThrow().getCreatedBy().getId());
        String html=mvc.perform(get("/admin/users/"+staff.getId()+"/edit").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("name=\"roleCode\""));
        assertFalse(html.contains("name=\"roleId\""));
    }

    @Test void publicForgotPasswordDoesNotDisableCurrentLoginAndLockedTokenCannotSetPassword() {
        String original=customer.getPasswordHash();
        auth.initiatePasswordReset(customer.getEmail());
        var links=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(customer.getEmail()),anyString(),links.capture());
        String token=java.net.URI.create(links.getValue()).getQuery().substring(6);
        assertEquals(original,users.findById(customer.getId()).orElseThrow().getPasswordHash());
        assertFalse(customer.getMustChangePassword());
        customer.setStatus("LOCKED");users.saveAndFlush(customer);
        assertFalse(auth.verifyPasswordResetToken(token));
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(token,"Locked@123"));
        assertEquals("LOCKED",users.findById(customer.getId()).orElseThrow().getStatus());
    }
    @Test void checkoutAndCancelRestoreStockAndVoucherExactlyOnce() {
        Voucher v=voucherService.createVoucher(voucherForm());voucherService.saveVoucherToWallet(customer.getId(),v.getId());
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        var form=checkout();form.setVoucherCode(v.getCode());
        Order order=orderService.checkoutOrder(customer.getId(),form);
        em.flush();em.clear();
        assertEquals(8,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(1,vouchers.findById(v.getId()).orElseThrow().getUsedCount());
        assertEquals(0,new BigDecimal("390000").compareTo(order.getTotalAmount()));
        orderService.customerCancelOrder(order.getId(),customer.getId(),"Changed mind");
        em.flush();em.clear();
        assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0,vouchers.findById(v.getId()).orElseThrow().getUsedCount());
        assertThrows(IllegalStateException.class,()->orderService.customerCancelOrder(order.getId(),customer.getId(),"Again"));
        assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }
    @Test void walletUsesOrderCountWhenVoucherAllowsMultipleUses() {
        Voucher v=voucherService.createVoucher(voucherForm());voucherService.saveVoucherToWallet(customer.getId(),v.getId());
        carts.addItemToCart(customer.getId(),variant.getId(),1);var form=checkout();form.setVoucherCode(v.getCode());
        Order first=orderService.checkoutOrder(customer.getId(),form);
        em.flush();em.clear();
        assertEquals(1,voucherService.getCustomerWalletVouchers(customer.getId()).get(0).getRemainingUses());
        carts.addItemToCart(customer.getId(),variant.getId(),1);Order second=orderService.checkoutOrder(customer.getId(),form);
        em.flush();em.clear();assertEquals(0,voucherService.getCustomerWalletVouchers(customer.getId()).get(0).getRemainingUses());
        orderService.customerCancelOrder(first.getId(),customer.getId(),"Cancel one");em.flush();em.clear();
        assertEquals(1,voucherService.getCustomerWalletVouchers(customer.getId()).get(0).getRemainingUses());
        assertTrue(wallet.findByCustomerIdAndVoucherId(customer.getId(),v.getId()).orElseThrow().getIsUsed());
        orderService.customerCancelOrder(second.getId(),customer.getId(),"Cancel second");em.flush();em.clear();
        assertFalse(wallet.findByCustomerIdAndVoucherId(customer.getId(),v.getId()).orElseThrow().getIsUsed());
    }
    @Test void onlySelectedCartItemsContributeToCheckout() {
        var cart=carts.addItemToCart(customer.getId(),variant.getId(),1);
        carts.selectItem(customer.getId(),cart.getItems().get(0).getId(),false);
        assertEquals(0,BigDecimal.ZERO.compareTo(carts.getCartForUser(customer.getId()).getSubtotal()));
        assertThrows(IllegalArgumentException.class,()->orderService.checkoutOrder(customer.getId(),checkout()));
        carts.selectItem(customer.getId(),cart.getItems().get(0).getId(),true);
        assertEquals(0,new BigDecimal("200000").compareTo(carts.getCartForUser(customer.getId()).getSubtotal()));
    }
    @Test void hiddenProductCannotBeBoughtOrViewed() {
        product.setStatus(ProductStatus.HIDDEN);products.saveAndFlush(product);
        assertThrows(IllegalArgumentException.class,()->carts.addItemToCart(customer.getId(),variant.getId(),1));
        assertThrows(IllegalArgumentException.class,()->catalog.getProductBySlug(product.getSlug()));
    }
    @Test void combinedCatalogFiltersAndPriceSortingWork() {
        var filter=ProductFilterDto.builder().keyword("Dog").categoryCode("FOOD").petType(PetType.DOG).minPrice(new BigDecimal("190000")).maxPrice(new BigDecimal("210000")).sortBy("price_desc").build();
        assertEquals(1,catalog.searchAndFilterProducts(filter,PageRequest.of(0,10)).getTotalElements());
        filter.setPetType(PetType.CAT);assertEquals(0,catalog.searchAndFilterProducts(filter,PageRequest.of(0,10)).getTotalElements());
        filter.setPetType(PetType.DOG);filter.setMinPrice(new BigDecimal("250000"));assertEquals(0,catalog.searchAndFilterProducts(filter,PageRequest.of(0,10)).getTotalElements());
    }
    @Test void unavailableVnpayCannotCreateFalseSuccessOrder() {
        carts.addItemToCart(customer.getId(),variant.getId(),1);var form=checkout();form.setPaymentMethod(PaymentMethod.VNPAY);
        assertThrows(IllegalStateException.class,()->placementService.place(customer.getId(),form,"127.0.0.1"));
        assertEquals(0,orders.count());assertEquals(10,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
    }
    @Test void stockImportRequiresItemsAndProductVariantStartsAtZero() {
        assertThrows(IllegalArgumentException.class,()->imports.createStockImport(new StockImport(),List.of(),staff.getId()));
        var pv=ProductVariant.builder().skuCode("REG-NEW").variantName("New").price(BigDecimal.TEN).stockQuantity(500).build();
        assertEquals(0,manage.addVariant(product.getId(),pv).getStockQuantity());
    }
    @Autowired ProductManageService manage;
    @Test void reportsRenderWithNoCompletedOrdersAndNoReviews() throws Exception {
        for (String path : List.of("/admin/reports/revenue?groupBy=month","/admin/reports/revenue?groupBy=year","/admin/reports/stock","/admin/reports/reviews"))
            mvc.perform(get(path).with(user(new CustomUserDetails(admin)))).andExpect(status().isOk());
        assertEquals(1,reports.stockReconciliation().size());
        assertEquals(0.0,reviews.getOverallAverageRating());
    }
    @Test void staffCanOpenOwnProfileAndCustomerStatusFilterWorks() throws Exception {
        mvc.perform(get("/account/profile").with(user(new CustomUserDetails(staff)))).andExpect(status().isOk());
        assertEquals(1,userService.getAllUsers(null,"CUSTOMER","ACTIVE",PageRequest.of(0,10)).getTotalElements());
        assertEquals(0,userService.getAllUsers(null,"CUSTOMER","LOCKED",PageRequest.of(0,10)).getTotalElements());
    }

    @Test void completedRevenueExcludesShippingAndReviewRedirectKeepsKeyword() throws Exception {
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        var order=orderService.checkoutOrder(customer.getId(),checkout());
        orderService.confirmOrder(order.getId(),staff.getId());
        orderService.markPreparing(order.getId(),staff.getId());
        orderService.markShipping(order.getId(),staff.getId());
        orderService.markCompleted(order.getId(),staff.getId());
        em.flush();
        var view=mvc.perform(get("/admin/reports/revenue?groupBy=month").with(user(new CustomUserDetails(admin)))).andExpect(status().isOk()).andReturn().getModelAndView();
        assertEquals(0,new BigDecimal("400000").compareTo((BigDecimal)view.getModel().get("totalRevenue")));
        assertEquals(0,new BigDecimal("430000").compareTo((BigDecimal)view.getModel().get("totalCollected")));
        assertEquals(1,reports.topSelling(LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).size());
        var item=orderItems.findByOrderId(order.getId()).get(0);
        var review=reviews.saveAndFlush(Review.builder().orderItem(item).customer(customer).rating(4).comment("Good").status(ReviewStatus.VISIBLE).build());
        var response=mvc.perform(post("/admin/reviews/"+review.getId()+"/toggle-status").with(user(new CustomUserDetails(admin))).with(csrf()).param("keyword","a&status=HIDDEN")).andReturn().getResponse();
        assertTrue(response.getRedirectedUrl().contains("keyword=a%26status%3DHIDDEN"));
        assertEquals(1,reports.reviewsByProduct(LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).size());
    }

    @Test void passwordResetTokenCanOnlyBeUsedOnceAndRejectsWeakPassword() throws Exception {
        String raw="regression-token";
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var token=tokens.saveAndFlush(PasswordResetToken.builder().user(customer).tokenHash(hash).expiresAt(LocalDateTime.now().plusHours(1)).build());
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(raw,"123"));
        assertNull(tokens.findById(token.getId()).orElseThrow().getUsedAt());
        auth.resetPasswordWithToken(raw,"Reset@123");
        assertTrue(encoder.matches("Reset@123",users.findById(customer.getId()).orElseThrow().getPasswordHash()));
        assertThrows(IllegalArgumentException.class,()->auth.resetPasswordWithToken(raw,"Another@123"));
    }

    @Test void softDeletePreservesUsedVoucherOrderAndCancellation() throws Exception {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        voucherService.saveVoucherToWallet(customer.getId(), voucher.getId());
        carts.addItemToCart(customer.getId(), variant.getId(), 2);
        var form = checkout(); form.setVoucherCode(voucher.getCode());
        Order order = orderService.checkoutOrder(customer.getId(), form);
        mvc.perform(post("/admin/vouchers/" + voucher.getId() + "/delete")
                .with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/vouchers"));
        em.flush(); em.clear();
        Voucher deleted = vouchers.findById(voucher.getId()).orElseThrow();
        assertTrue(deleted.getIsDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(VoucherStatus.INACTIVE, deleted.getStatus());
        assertEquals(1, deleted.getUsedCount());
        Order persisted = orders.findById(order.getId()).orElseThrow();
        assertEquals(voucher.getId(), persisted.getVoucher().getId());
        assertEquals(voucher.getCode(), persisted.getVoucherCodeSnapshot());
        assertEquals(0, new BigDecimal("390000").compareTo(persisted.getTotalAmount()));
        mvc.perform(get("/customer/orders/" + order.getId()).with(user(new CustomUserDetails(customer))))
                .andExpect(status().isOk());
        assertTrue(voucherService.getCustomerWalletVouchers(customer.getId()).isEmpty());
        assertTrue(wallet.findByCustomerIdAndVoucherId(customer.getId(), voucher.getId()).isPresent());
        orderService.customerCancelOrder(order.getId(), customer.getId(), "Cancel after voucher deletion");
        em.flush(); em.clear();
        assertEquals(10, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, vouchers.findById(voucher.getId()).orElseThrow().getUsedCount());
        assertTrue(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
    }

    @Test void deletedVoucherCannotBeSavedEditedActivatedReservedOrCheckedOut() {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        voucherService.softDeleteVoucher(voucher.getId()); em.flush(); em.clear();
        assertFalse(voucherService.validateAndCalculateDiscount(voucher.getCode(), customer.getId(), new BigDecimal("400000")).isValid());
        assertTrue(vouchers.findAvailablePublicVouchers(LocalDateTime.now()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> voucherService.saveVoucherToWallet(customer.getId(), voucher.getId()));
        assertThrows(IllegalArgumentException.class, () -> voucherService.getVoucherById(voucher.getId()));
        assertThrows(IllegalArgumentException.class, () -> voucherService.updateVoucher(voucher.getId(), voucherForm()));
        assertThrows(IllegalArgumentException.class, () -> voucherService.toggleVoucherStatus(voucher.getId()));
        assertThrows(IllegalStateException.class, () -> voucherService.reserveVoucher(voucher.getId()));
        carts.addItemToCart(customer.getId(), variant.getId(), 2);
        var form = checkout(); form.setVoucherCode(voucher.getCode());
        assertThrows(IllegalArgumentException.class, () -> orderService.checkoutOrder(customer.getId(), form));
        assertEquals(0, orders.count());
        assertEquals(10, variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, vouchers.findById(voucher.getId()).orElseThrow().getUsedCount());
        assertEquals(1, carts.getCartForUser(customer.getId()).getItems().size());
    }

    @Test void deletedVoucherListRendersSeparatelyAndKeepsPaginationFilter() throws Exception {
        Voucher first = voucherService.createVoucher(voucherForm());
        var secondForm = voucherForm(); secondForm.setCode("REG11");
        Voucher second = voucherService.createVoucher(secondForm);
        var before = mvc.perform(get("/admin/vouchers").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(before.contains("/admin/vouchers/" + first.getId() + "/delete"));
        assertTrue(before.contains("name=\"_csrf\""));
        assertFalse(before.contains("Chưa xóa"));
        assertFalse(before.contains("<select name=\"deleted\""));
        assertTrue(before.contains("deleted=true"));
        voucherService.softDeleteVoucher(first.getId()); voucherService.softDeleteVoucher(second.getId()); em.flush();
        assertEquals(0, voucherService.getAllVouchers(PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, voucherService.searchVouchers(null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, voucherService.searchVouchers(null, null, true, PageRequest.of(0, 10)).getTotalElements());
        var html = mvc.perform(get("/admin/vouchers?deleted=true&size=1").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Đã xóa"));
        assertTrue(html.contains("deleted=true"));
        assertFalse(html.contains("/delete\""));
        assertFalse(html.contains("/toggle-status\""));
        assertTrue(html.contains("/restore\""));
        assertTrue(html.contains("Phục hồi"));
        mvc.perform(get("/admin/vouchers/" + first.getId() + "/edit").with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/admin/vouchers")).andExpect(flash().attributeExists("errorMessage"));
    }

    @Test void onlyAdminWithCsrfCanSoftDeleteVoucher() throws Exception {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        String path = "/admin/vouchers/" + voucher.getId() + "/delete";
        for (User denied : List.of(customer, staff)) {
            mvc.perform(post(path).with(user(new CustomUserDetails(denied))).with(csrf()))
                    .andExpect(redirectedUrl("/error/403"));
        }
        mvc.perform(post(path).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post(path).with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/error/403"));
        assertFalse(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
        mvc.perform(post(path).with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/vouchers"));
        assertTrue(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
    }

    @Test void repeatedSoftDeletionKeepsTimestampAndReservesVoucherCode() {
        Voucher voucher = voucherService.createVoucher(voucherForm());
        voucherService.softDeleteVoucher(voucher.getId()); em.flush(); em.clear();
        var timestamp = vouchers.findById(voucher.getId()).orElseThrow().getDeletedAt();
        voucherService.softDeleteVoucher(voucher.getId()); em.flush(); em.clear();
        assertEquals(timestamp, vouchers.findById(voucher.getId()).orElseThrow().getDeletedAt());
        assertEquals(1, vouchers.count());
        assertThrows(IllegalArgumentException.class, () -> voucherService.createVoucher(voucherForm()));
    }

    @Test void adminEditAfterAtomicQuotaReservationKeepsUpdatedCount() {
        Voucher v=voucherService.createVoucher(voucherForm());
        voucherService.reserveVoucher(v.getId());
        var form=voucherForm();form.setTitle("Updated");
        voucherService.updateVoucher(v.getId(),form);em.flush();em.clear();
        assertEquals(1,vouchers.findById(v.getId()).orElseThrow().getUsedCount());
    }

    @Test void restoringVoucherPreservesCampaignOrderAndWalletHistory() throws Exception {
        var request=voucherForm();request.setPerCustomerLimit(1);
        Voucher voucher=voucherService.createVoucher(request);
        voucherService.saveVoucherToWallet(customer.getId(),voucher.getId());
        carts.addItemToCart(customer.getId(),variant.getId(),2);
        var checkout=checkout();checkout.setVoucherCode(voucher.getCode());
        Order order=orderService.checkoutOrder(customer.getId(),checkout);
        em.flush();em.clear();
        var original=vouchers.findById(voucher.getId()).orElseThrow();
        var createdAt=original.getCreatedAt();var start=original.getStartDate();var end=original.getEndDate();
        var walletBefore=wallet.findByCustomerIdAndVoucherId(customer.getId(),voucher.getId()).orElseThrow();
        var walletId=walletBefore.getId();var savedAt=walletBefore.getSavedAt();var usedAt=walletBefore.getUsedAt();
        voucherService.softDeleteVoucher(voucher.getId());
        mvc.perform(post("/admin/vouchers/"+voucher.getId()+"/restore").with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/vouchers")).andExpect(flash().attributeExists("successMessage"));
        em.flush();em.clear();
        var restored=voucherService.getVoucherById(voucher.getId());
        assertFalse(restored.getIsDeleted());assertNull(restored.getDeletedAt());
        assertEquals(VoucherStatus.INACTIVE,restored.getStatus());
        assertEquals(voucher.getCode(),restored.getCode());assertEquals(createdAt,restored.getCreatedAt());
        assertEquals(start,restored.getStartDate());assertEquals(end,restored.getEndDate());
        assertEquals(1,restored.getUsedCount());assertEquals(1,restored.getPerCustomerLimit());assertEquals(10,restored.getTotalUsageLimit());
        var persisted=orders.findById(order.getId()).orElseThrow();
        assertEquals(restored.getId(),persisted.getVoucher().getId());assertEquals(voucher.getCode(),persisted.getVoucherCodeSnapshot());
        assertEquals(0,new BigDecimal("390000").compareTo(persisted.getTotalAmount()));
        var walletAfter=wallet.findByCustomerIdAndVoucherId(customer.getId(),voucher.getId()).orElseThrow();
        assertEquals(walletId,walletAfter.getId());assertEquals(savedAt,walletAfter.getSavedAt());assertEquals(usedAt,walletAfter.getUsedAt());
        assertTrue(walletAfter.getIsUsed());assertEquals(8,variants.findById(variant.getId()).orElseThrow().getStockQuantity());
        assertEquals(0,voucherService.searchVouchers(null,null,true,PageRequest.of(0,10)).getTotalElements());
        assertEquals(1,voucherService.getAllVouchers(PageRequest.of(0,10)).getTotalElements());
        assertFalse(voucherService.validateAndCalculateDiscount(voucher.getCode(),null,new BigDecimal("400000")).isValid());
        voucherService.toggleVoucherStatus(voucher.getId());
        assertFalse(voucherService.validateAndCalculateDiscount(voucher.getCode(),customer.getId(),new BigDecimal("400000")).isValid());
        assertTrue(voucherService.validateAndCalculateDiscount(voucher.getCode(),null,new BigDecimal("400000")).isValid());
    }

    @Test void onlyAdminWithCsrfCanRestoreDeletedVoucher() throws Exception {
        Voucher voucher=voucherService.createVoucher(voucherForm());voucherService.softDeleteVoucher(voucher.getId());
        String path="/admin/vouchers/"+voucher.getId()+"/restore";
        for(User denied:List.of(customer,staff)) {
            mvc.perform(post(path).with(user(new CustomUserDetails(denied))).with(csrf())).andExpect(redirectedUrl("/error/403"));
        }
        mvc.perform(post(path).with(csrf())).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post(path).with(user(new CustomUserDetails(admin)))).andExpect(redirectedUrl("/error/403"));
        assertTrue(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
        mvc.perform(post(path).with(user(new CustomUserDetails(admin))).with(csrf())).andExpect(redirectedUrl("/admin/vouchers"));
        assertFalse(vouchers.findById(voucher.getId()).orElseThrow().getIsDeleted());
        mvc.perform(post("/admin/vouchers/999999/restore").with(user(new CustomUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/vouchers?deleted=true")).andExpect(flash().attributeExists("errorMessage"));
    }

    @Test void restoringVoucherIsIdempotentAndDoesNotRenewDatesOrResetQuota() {
        var form=voucherForm();form.setStartDate(LocalDateTime.now().minusDays(3));form.setEndDate(LocalDateTime.now().minusDays(2));
        var voucher=voucherService.createVoucher(form);voucher.setUsedCount(10);vouchers.saveAndFlush(voucher);em.refresh(voucher);
        var createdAt=voucher.getCreatedAt();var persistedEnd=voucher.getEndDate();
        voucherService.softDeleteVoucher(voucher.getId());voucherService.restoreDeletedVoucher(voucher.getId());
        var restored=voucherService.getVoucherById(voucher.getId());
        assertEquals(persistedEnd,restored.getEndDate());assertEquals(10,restored.getUsedCount());
        voucherService.toggleVoucherStatus(voucher.getId());
        voucherService.restoreDeletedVoucher(voucher.getId());
        assertEquals(VoucherStatus.ACTIVE,restored.getStatus());assertEquals(createdAt,restored.getCreatedAt());
        assertFalse(voucherService.validateAndCalculateDiscount(voucher.getCode(),null,new BigDecimal("400000")).isValid());
        var updated=voucherForm();voucherService.updateVoucher(voucher.getId(),updated);
        assertFalse(voucherService.validateAndCalculateDiscount(voucher.getCode(),null,new BigDecimal("400000")).isValid());
        updated.setTotalUsageLimit(11);voucherService.updateVoucher(voucher.getId(),updated);
        assertTrue(voucherService.validateAndCalculateDiscount(voucher.getCode(),null,new BigDecimal("400000")).isValid());
        assertEquals(10,restored.getUsedCount());assertEquals(1,vouchers.count());
    }

    @Test void deletedCodesStayReservedForCreateAndEditWithRecoveryGuidance() throws Exception {
        var deleted=voucherService.createVoucher(voucherForm());voucherService.softDeleteVoucher(deleted.getId());
        var request=voucherForm();request.setCode(" reg10 ");
        var error=assertThrows(IllegalArgumentException.class,()->voucherService.createVoucher(request));
        assertTrue(error.getMessage().contains("phục hồi"));
        var another=voucherForm();another.setCode("OTHER10");var existing=voucherService.createVoucher(another);
        assertThrows(IllegalArgumentException.class,()->voucherService.updateVoucher(existing.getId(),voucherForm()));
        assertEquals("OTHER10",vouchers.findById(existing.getId()).orElseThrow().getCode());
        mvc.perform(post("/admin/vouchers/create").with(user(new CustomUserDetails(admin))).with(csrf()).flashAttr("voucherForm",voucherForm()))
                .andExpect(status().isOk()).andExpect(view().name("admin/voucher-form")).andExpect(model().attributeExists("errorMessage"));
        assertEquals(2,vouchers.count());assertTrue(vouchers.findById(deleted.getId()).orElseThrow().getIsDeleted());
    }

    @Test void customerAndStaffProfilePasswordAndAddressBookWorkflowsWorkCleanly() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "C@REGRESSION.TEST").param("password", "Secret@123"))
                .andExpect(redirectedUrl("/"));
        assertNotNull(users.findById(customer.getId()).orElseThrow().getLastLoginAt());

        var customerPrincipal = new CustomUserDetails(users.findById(customer.getId()).orElseThrow());
        String profileHtml = mvc.perform(get("/customer/profile").with(user(customerPrincipal)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(profileHtml.contains("id=\"editAddressForm\""));

        mvc.perform(post("/customer/profile").with(user(customerPrincipal)).with(csrf())
                .param("fullName", "Updated Customer").param("phone", "0909888777"))
                .andExpect(redirectedUrl("/customer/profile")).andExpect(flash().attributeExists("successMessage"));
        assertEquals("Updated Customer", users.findById(customer.getId()).orElseThrow().getFullName());
        assertEquals("0909888777", users.findById(customer.getId()).orElseThrow().getPhone());

        mvc.perform(post("/customer/change-password").with(user(customerPrincipal)).with(csrf())
                .param("currentPassword", "Secret@123").param("newPassword", "123").param("confirmNewPassword", "123"))
                .andExpect(redirectedUrl("/customer/profile")).andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(post("/customer/change-password").with(user(customerPrincipal)).with(csrf())
                .param("currentPassword", "Secret@123").param("newPassword", "NewCust@123").param("confirmNewPassword", "NewCust@123"))
                .andExpect(redirectedUrl("/customer/profile")).andExpect(flash().attributeExists("successMessage"));
        assertTrue(encoder.matches("NewCust@123", users.findById(customer.getId()).orElseThrow().getPasswordHash()));

        var refreshedCustomer = new CustomUserDetails(users.findById(customer.getId()).orElseThrow());
        mvc.perform(post("/customer/addresses/add").with(user(refreshedCustomer)).with(csrf())
                .param("recipientName", "").param("recipientPhone", "invalid"))
                .andExpect(redirectedUrl("/customer/addresses")).andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(post("/customer/addresses/add").with(user(refreshedCustomer)).with(csrf())
                .param("recipientName", "Addr 1").param("recipientPhone", "0901111222")
                .param("province", "TP.HCM").param("district", "Quận 1").param("ward", "Bến Nghé").param("addressLine", "1 Lê Lợi"))
                .andExpect(redirectedUrl("/customer/addresses")).andExpect(flash().attributeExists("successMessage"));
        var addrList1 = userService.getCustomerAddresses(customer.getId());
        assertEquals(1, addrList1.size());
        assertTrue(addrList1.get(0).getIsDefault());
        Long addr1Id = addrList1.get(0).getId();

        mvc.perform(post("/customer/addresses/add").with(user(refreshedCustomer)).with(csrf())
                .param("recipientName", "Addr 2").param("recipientPhone", "0903333444")
                .param("province", "Hà Nội").param("district", "Ba Đình").param("ward", "Điện Biên").param("addressLine", "2 Độc Lập")
                .param("isDefault", "false").param("source", "profile"))
                .andExpect(redirectedUrl("/customer/profile?tab=addresses")).andExpect(flash().attributeExists("successMessage"));
        var addrList2 = userService.getCustomerAddresses(customer.getId());
        assertEquals(2, addrList2.size());
        Long addr2Id = addrList2.stream().filter(a -> !a.getId().equals(addr1Id)).findFirst().orElseThrow().getId();

        mvc.perform(post("/customer/addresses/" + addr2Id + "/edit").with(user(refreshedCustomer)).with(csrf())
                .param("recipientName", "Addr 2 Edited").param("recipientPhone", "0905555666")
                .param("province", "Đà Nẵng").param("district", "Hải Châu").param("ward", "Thạch Thang").param("addressLine", "3 Bạch Đằng")
                .param("isDefault", "true"))
                .andExpect(redirectedUrl("/customer/addresses")).andExpect(flash().attributeExists("successMessage"));
        assertTrue(addresses.findById(addr2Id).orElseThrow().getIsDefault());
        assertFalse(addresses.findById(addr1Id).orElseThrow().getIsDefault());
        assertEquals("Addr 2 Edited", addresses.findById(addr2Id).orElseThrow().getRecipientName());

        mvc.perform(post("/customer/addresses/" + addr2Id + "/set-default").with(user(refreshedCustomer)).with(csrf()))
                .andExpect(redirectedUrl("/customer/addresses")).andExpect(flash().attributeExists("successMessage"));
        assertTrue(addresses.findById(addr2Id).orElseThrow().getIsDefault());

        mvc.perform(post("/customer/addresses/" + addr2Id + "/delete").with(user(refreshedCustomer)).with(csrf()))
                .andExpect(redirectedUrl("/customer/addresses")).andExpect(flash().attributeExists("successMessage"));
        var remainingAddrs = userService.getCustomerAddresses(customer.getId());
        assertEquals(1, remainingAddrs.size());
        assertEquals(addr1Id, remainingAddrs.get(0).getId());
        assertTrue(remainingAddrs.get(0).getIsDefault());

        var staffPrincipal = new CustomUserDetails(users.findById(staff.getId()).orElseThrow());
        mvc.perform(get("/staff/profile").with(user(staffPrincipal))).andExpect(status().isOk());
        mvc.perform(post("/staff/profile").with(user(staffPrincipal)).with(csrf())
                .param("fullName", "Updated Staff").param("phone", "0907777888"))
                .andExpect(redirectedUrl("/staff/profile")).andExpect(flash().attributeExists("successMessage"));
        assertEquals("Updated Staff", users.findById(staff.getId()).orElseThrow().getFullName());

        mvc.perform(post("/staff/change-password").with(user(staffPrincipal)).with(csrf())
                .param("currentPassword", "Secret@123").param("newPassword", "short").param("confirmNewPassword", "short"))
                .andExpect(redirectedUrl("/staff/profile")).andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(post("/staff/change-password").with(user(staffPrincipal)).with(csrf())
                .param("currentPassword", "Secret@123").param("newPassword", "NewStaff@123").param("confirmNewPassword", "NewStaff@123"))
                .andExpect(redirectedUrl("/staff/profile")).andExpect(flash().attributeExists("successMessage"));
        assertTrue(encoder.matches("NewStaff@123", users.findById(staff.getId()).orElseThrow().getPasswordHash()));

        mvc.perform(post("/logout").with(csrf()))
                .andExpect(redirectedUrl("/login?logout=true"));
    }
}
