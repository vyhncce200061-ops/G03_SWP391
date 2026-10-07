package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.cart.CartDto;
import com.petshop.service.CartService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public String viewCart(@AuthenticationPrincipal CustomUserDetails userDetails,
                           HttpSession session,
                           Model model) {
        CartDto cart = cartService.getCartForUser(userDetails.getId());
        session.setAttribute("cartCount", cart.getTotalQuantity());
        model.addAttribute("cart", cart);
        return "customer/cart";
    }

    @PostMapping("/add")
    public String addToCart(@AuthenticationPrincipal CustomUserDetails userDetails,
                            @RequestParam Long variantId,
                            @RequestParam(defaultValue = "1") int quantity,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            CartDto cart = cartService.addItemToCart(userDetails.getId(), variantId, quantity);
            session.setAttribute("cartCount", cart.getTotalQuantity());
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ hàng thành công!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/update")
    public String updateQuantity(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @RequestParam Long itemId,
                                 @RequestParam int quantity,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        try {
            CartDto cart = cartService.updateItemQuantity(userDetails.getId(), itemId, quantity);

            session.setAttribute("cartCount", cart.getTotalQuantity());

            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật số lượng sản phẩm.");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeItem(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @RequestParam Long itemId,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            CartDto cart = cartService.removeItem(userDetails.getId(), itemId);

            session.setAttribute("cartCount", cart.getTotalQuantity());

            redirectAttributes.addFlashAttribute("infoMessage", "Đã xóa sản phẩm khỏi giỏ hàng.");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/clear")
    public String clearCart(@AuthenticationPrincipal CustomUserDetails userDetails,
                            HttpSession session) {
        cartService.clearCart(userDetails.getId());
        session.setAttribute("cartCount", 0);
        return "redirect:/cart";
    }
}
