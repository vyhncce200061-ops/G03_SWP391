package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.cart.CartDto;
import com.petshop.dto.order.CheckoutQuoteDto;
import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.entity.Order;
import com.petshop.entity.UserAddress;
import com.petshop.entity.enums.PaymentMethod;
import com.petshop.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {
    private final CartService cartService;
    private final OrderService orderService;
    private final UserService userService;
    private final VoucherService voucherService;
    private final CheckoutPricingService pricingService;
    private final VnPayService vnPayService;
    private final CheckoutPlacementService placementService;

    @GetMapping
    public String checkoutPage(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        if (!model.containsAttribute("checkoutForm")) {
            CheckoutSubmitDto form = new CheckoutSubmitDto();
            form.setPaymentMethod(PaymentMethod.COD);
            List<UserAddress> addresses = userService.getCustomerAddresses(user.getId());
            UserAddress address = addresses.stream().filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                    .findFirst().orElse(addresses.isEmpty() ? null : addresses.get(0));
            if (address != null) {
                form.setRecipientName(address.getRecipientName());
                form.setRecipientPhone(address.getRecipientPhone());
                form.setShippingProvince(address.getProvince());
                form.setShippingDistrict(address.getDistrict());
                form.setShippingWard(address.getWard());
                form.setShippingAddressLine(address.getAddressLine());
            }
            model.addAttribute("checkoutForm", form);
        }
        preparePage(user.getId(), (CheckoutSubmitDto) model.getAttribute("checkoutForm"), model);
        return "customer/checkout";
    }

    @PostMapping("/apply-voucher")
    @ResponseBody
    public VoucherApplyResultDto applyVoucher(@AuthenticationPrincipal CustomUserDetails user,
                                              @RequestParam String code) {
        CartDto cart = cartService.getCartForUser(user.getId());
        if (code.length() > 50 || !cart.isCheckoutAllowed()) {
            return VoucherApplyResultDto.builder().valid(false)
                    .message("Vui lòng kiểm tra mã giảm giá và chọn sản phẩm hợp lệ trong giỏ.").build();
        }
        return voucherService.validateAndCalculateDiscount(code, user.getId(), cart.getSubtotal());
    }

    @PostMapping("/quote")
    @ResponseBody
    public CheckoutQuoteDto quote(@AuthenticationPrincipal CustomUserDetails user,
                                   @RequestParam(defaultValue = "") String code) {
        return pricingService.quote(cartService.getCartForUser(user.getId()), user.getId(), code);
    }

    @PostMapping("/place-order")
    public String placeOrder(@AuthenticationPrincipal CustomUserDetails user,
                             @Valid @ModelAttribute("checkoutForm") CheckoutSubmitDto form,
                             BindingResult errors, HttpSession session,
                             HttpServletRequest request, Model model) {
        CheckoutQuoteDto quote = preparePage(user.getId(), form, model);
        if (errors.hasErrors()) {
            return "customer/checkout";
        }
        if (!quote.isCheckoutAllowed()) {
            model.addAttribute("checkoutError", quote.getMessage());
            return "customer/checkout";
        }
        if (form.getPaymentMethod() == PaymentMethod.VNPAY && !vnPayService.isConfigured()) {
            model.addAttribute("checkoutError", "VNPay chưa được cấu hình. Vui lòng chọn COD.");
            return "customer/checkout";
        }
        try {
            CheckoutPlacementService.Placement placement = placementService.place(user.getId(), form, request.getRemoteAddr());
            Order order = placement.order();
            session.setAttribute("cartCount", cartService.getCartItemCount(user.getId()));
            if (order.getPaymentMethod() == PaymentMethod.VNPAY) {
                return "redirect:" + placement.paymentUrl();
            }
            return "redirect:/checkout/success/" + order.getOrderCode();
        } catch (IllegalArgumentException | IllegalStateException e) {
            // The order transaction has rolled back. Re-read prices/selection for the next attempt.
            preparePage(user.getId(), form, model);
            model.addAttribute("checkoutError", e.getMessage());
            return "customer/checkout";
        }
    }

    @GetMapping("/success/{code}")
    public String orderSuccess(@AuthenticationPrincipal CustomUserDetails user,
                               @PathVariable String code, Model model) {
        try {
            Order order = orderService.getOrderByCodeForCustomer(code, user.getId());
            model.addAttribute("orderCode", order.getOrderCode());
            return "customer/order-success";
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
        }
    }

    private CheckoutQuoteDto preparePage(Long customerId, CheckoutSubmitDto form, Model model) {
        CartDto cart = cartService.getCartForUser(customerId);
        CheckoutQuoteDto quote = pricingService.quote(cart, customerId, form.getVoucherCode());
        model.addAttribute("quote", quote);
        model.addAttribute("addresses", userService.getCustomerAddresses(customerId));
        model.addAttribute("vnPayAvailable", vnPayService.isConfigured());
        return quote;
    }
}
