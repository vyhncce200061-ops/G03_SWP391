package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.cart.CartDto;
import com.petshop.service.CartService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Map;
import java.util.function.Supplier;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/api")
    @ResponseBody
    public CartDto getCart(@AuthenticationPrincipal CustomUserDetails userDetails,
                           HttpSession session) {
        CartDto cart = cartService.getCartForUser(userDetails.getId());
        session.setAttribute("cartCount", cart.getTotalQuantity());
        return cart;
    }

    @PostMapping("/api/quantity")
    @ResponseBody
    public ResponseEntity<?> updateQuantityApi(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @RequestParam Long itemId,
                                               @RequestParam int quantity,
                                               HttpSession session) {
        return mutateCart(userDetails.getId(), session,
                () -> cartService.updateItemQuantity(userDetails.getId(), itemId, quantity));
    }

    @PostMapping("/api/selection")
    @ResponseBody
    public ResponseEntity<?> updateSelectionApi(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                @RequestParam Long itemId,
                                                @RequestParam boolean selected,
                                                HttpSession session) {
        return mutateCart(userDetails.getId(), session,
                () -> cartService.updateItemSelection(userDetails.getId(), itemId, selected));
    }

    private ResponseEntity<?> mutateCart(Long userId, HttpSession session, Supplier<CartDto> mutation) {
        try {
            CartDto cart = mutation.get();
            session.setAttribute("cartCount", cart.getTotalQuantity());
            return ResponseEntity.ok(cart);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return cartError(userId, session, e.getMessage());
        }
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    @ResponseBody
    public ResponseEntity<?> invalidCartRequest(Principal principal, HttpSession session) {
        CustomUserDetails userDetails = (CustomUserDetails) ((Authentication) principal).getPrincipal();
        return cartError(userDetails.getId(), session,
                "Dữ liệu giỏ hàng không hợp lệ. Vui lòng kiểm tra sản phẩm và số lượng nguyên dương.");
    }

    private ResponseEntity<?> cartError(Long userId, HttpSession session, String message) {
        CartDto cart = cartService.getCartForUser(userId);
        session.setAttribute("cartCount", cart.getTotalQuantity());
        return ResponseEntity.badRequest().body(Map.of("message", message, "cart", cart));
    }

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
    public Object updateQuantity(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @RequestParam Long itemId,
                                 @RequestParam int quantity,
                                 @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        try {
            CartDto cart = quantity <= 0 ? cartService.removeItem(userDetails.getId(), itemId)
                    : cartService.updateItemQuantity(userDetails.getId(), itemId, quantity);

            session.setAttribute("cartCount", cart.getTotalQuantity());
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) return ResponseEntity.ok(cart);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật số lượng sản phẩm.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
