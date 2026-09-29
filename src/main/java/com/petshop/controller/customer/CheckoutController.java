package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.cart.CartDto;
import com.petshop.dto.order.CheckoutSubmitDto;
import com.petshop.dto.voucher.VoucherApplyResultDto;
import com.petshop.entity.Order;
import com.petshop.entity.UserAddress;
import com.petshop.service.CartService;
import com.petshop.service.OrderService;
import com.petshop.service.UserService;
import com.petshop.service.VoucherService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
@Slf4j
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final UserService userService;
    private final VoucherService voucherService;

    @GetMapping
    public String checkoutPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                               Model model) {
        CartDto cart = cartService.getCartForUser(userDetails.getId());
        if (cart.getItems().isEmpty()) {
            return "redirect:/cart";
        }

        List<UserAddress> addresses = userService.getCustomerAddresses(userDetails.getId());
        UserAddress defaultAddress = addresses.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .findFirst()
                .orElse(addresses.isEmpty() ? null : addresses.get(0));

        if (!model.containsAttribute("checkoutForm")) {
            CheckoutSubmitDto form = new CheckoutSubmitDto();
            if (defaultAddress != null) {
                form.setRecipientName(defaultAddress.getRecipientName());
                form.setRecipientPhone(defaultAddress.getRecipientPhone());
                form.setShippingProvince(defaultAddress.getProvince());
                form.setShippingDistrict(defaultAddress.getDistrict());
                form.setShippingWard(defaultAddress.getWard());
                form.setShippingAddressLine(defaultAddress.getAddressLine());
            }
            model.addAttribute("checkoutForm", form);
        }

        model.addAttribute("cart", cart);
        model.addAttribute("addresses", addresses);
        return "customer/checkout";
    }

    @PostMapping("/apply-voucher")
    @ResponseBody
    public VoucherApplyResultDto applyVoucher(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @RequestParam String code) {
        CartDto cart = cartService.getCartForUser(userDetails.getId());
        return voucherService.validateAndCalculateDiscount(code, userDetails.getId(), cart.getSubtotal());
    }

    @PostMapping("/place-order")
    public String placeOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @Valid @ModelAttribute("checkoutForm") CheckoutSubmitDto form,
                             BindingResult bindingResult,
                             HttpSession session,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        CartDto cart = cartService.getCartForUser(userDetails.getId());
        if (bindingResult.hasErrors()) {
            model.addAttribute("cart", cart);
            model.addAttribute("addresses", userService.getCustomerAddresses(userDetails.getId()));
            return "customer/checkout";
        }

        try {
            Order order = orderService.checkoutOrder(userDetails.getId(), form);
            session.setAttribute("cartCount", 0);
            return "redirect:/checkout/success/" + order.getOrderCode();
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("cart", cart);
            model.addAttribute("addresses", userService.getCustomerAddresses(userDetails.getId()));
            model.addAttribute("errorMessage", e.getMessage());
            return "customer/checkout";
        }
    }

    @GetMapping("/success/{code}")
    public String orderSuccess(@PathVariable String code, Model model) {
        model.addAttribute("orderCode", code);
        return "customer/order-success";
    }
}
