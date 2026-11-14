package com.petshop.controller.customer;
import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.ReviewCreateDto;
import com.petshop.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequiredArgsConstructor @RequestMapping("/customer/reviews")
public class CustomerReviewController {
    private final ReviewService reviews;
    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue="0") int page, Model model) {
        model.addAttribute("reviews", reviews.myReviews(user.getId(), com.petshop.util.PageRequests.bounded(page,10)));
        return "customer/reviews";
    }
    @GetMapping("/create")
    public String form(@AuthenticationPrincipal CustomUserDetails user, @RequestParam Long orderItemId, Model model, RedirectAttributes flash) {
        try { model.addAttribute("item", reviews.eligibleItem(user.getId(), orderItemId)); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("errorMessage", e.getMessage());return "redirect:/customer/reviews"; }
        model.addAttribute("reviewForm",new ReviewCreateDto());return "customer/review-form";
    }
    @PostMapping("/create")
    public String create(@AuthenticationPrincipal CustomUserDetails user, @RequestParam Long orderItemId,
            @RequestParam(required = false) String redirectUrl,
            @Valid @ModelAttribute("reviewForm") ReviewCreateDto form, BindingResult errors, Model model, RedirectAttributes flash,
            @RequestHeader(value = "Referer", required = false) String referer,
            jakarta.servlet.http.HttpServletRequest request) {
        String target = returnTarget(redirectUrl, request);
        if (target == null) target = returnTarget(referer, request);
        if (target == null) target = "/customer/reviews";
        try {
            model.addAttribute("item", reviews.eligibleItem(user.getId(), orderItemId));
            if (errors.hasErrors()) {
                String firstError = errors.getAllErrors().isEmpty() ? "Dữ liệu đánh giá không hợp lệ." : errors.getAllErrors().get(0).getDefaultMessage();
                flash.addFlashAttribute("errorMessage", firstError);
                return "redirect:" + target;
            }
            reviews.createReview(user.getId(), orderItemId, form);
            flash.addFlashAttribute("successMessage", "Cảm ơn bạn! Đã gửi đánh giá sản phẩm thành công.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + target;
    }

    private String returnTarget(String value, jakarta.servlet.http.HttpServletRequest request) {
        if (value == null || value.isBlank()) return null;
        try {
            var uri = java.net.URI.create(value);
            if (uri.isAbsolute()) {
                int port = uri.getPort() < 0 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
                if (!request.getScheme().equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || !request.getServerName().equalsIgnoreCase(uri.getHost()) || request.getServerPort() != port
                        || uri.getRawUserInfo() != null) return null;
            } else if (uri.getRawAuthority() != null) return null;
            String path = uri.getPath();
            if (path == null || !path.matches("/customer/(reviews(?:/create)?|profile|orders/[0-9]+)")) return null;
            return path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
