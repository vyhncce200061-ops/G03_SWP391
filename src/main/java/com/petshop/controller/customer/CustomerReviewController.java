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
        model.addAttribute("reviews", reviews.myReviews(user.getId(), PageRequest.of(Math.max(0,page),10)));
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
            @Valid @ModelAttribute("reviewForm") ReviewCreateDto form, BindingResult errors, Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("item",reviews.eligibleItem(user.getId(),orderItemId));
            if (errors.hasErrors()) return "customer/review-form";
            reviews.createReview(user.getId(), orderItemId, form);
            flash.addFlashAttribute("successMessage","Đã gửi đánh giá sản phẩm.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/customer/reviews";
    }
}
