package com.petshop.controller.admin;

import com.petshop.entity.Review;
import com.petshop.entity.enums.ReviewStatus;
import com.petshop.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewManageController {

    private final ReviewService reviewService;

    @GetMapping
    public String listReviews(@RequestParam(required = false) String keyword,
                              @RequestParam(required = false) Integer rating,
                              @RequestParam(required = false) ReviewStatus status,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<Review> reviewPage = reviewService.searchReviews(keyword, rating, status, pageable);

        model.addAttribute("reviews", reviewPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("rating", rating);
        model.addAttribute("status", status);

        // Overall review statistics
        model.addAttribute("totalCount", reviewService.getTotalReviewsCount());
        model.addAttribute("visibleCount", reviewService.getVisibleReviewsCount());
        model.addAttribute("hiddenCount", reviewService.getHiddenReviewsCount());
        model.addAttribute("avgRating", reviewService.getAverageRating());

        return "admin/review-list";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Integer rating,
                               @RequestParam(required = false) ReviewStatus status,
                               @RequestParam(defaultValue = "0") int page,
                               RedirectAttributes redirectAttributes) {
        try {
            Review review = reviewService.toggleReviewStatus(id);
            String statusText = (review.getStatus() == ReviewStatus.VISIBLE) ? "HIỂN THỊ CÔNG KHAI" : "ẨN KHỎI CỬA HÀNG";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã chuyển trạng thái đánh giá #" + id + " sang: " + statusText);
        } catch (Exception e) {
            log.error("Error toggling review status for id {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }

        redirectAttributes.addAttribute("page", Math.max(0, page));
        if (keyword != null && !keyword.isBlank()) redirectAttributes.addAttribute("keyword", keyword.trim());
        if (rating != null) redirectAttributes.addAttribute("rating", rating);
        if (status != null) redirectAttributes.addAttribute("status", status.name());
        return "redirect:/admin/reviews";

    }
    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam(required = false) String replyText,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) Integer rating,
                        @RequestParam(required = false) ReviewStatus status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal
                        com.petshop.config.security.CustomUserDetails admin,
                        RedirectAttributes flash) {
        try {
            reviewService.reply(id, admin.getId(), replyText);
            flash.addFlashAttribute("successMessage", "Đã trả lời đánh giá của khách.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
            flash.addFlashAttribute("replyReviewId", id);
            flash.addFlashAttribute("replyDraft", replyText == null ? "" : replyText);
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Unable to save reply for review {}", id, e);
            flash.addFlashAttribute("errorMessage", "Chưa thể lưu trả lời. Vui lòng tải lại trang để kiểm tra trước khi gửi lại.");
            flash.addFlashAttribute("replyReviewId", id);
            flash.addFlashAttribute("replyDraft", replyText == null ? "" : replyText);
        }
        flash.addAttribute("page", Math.max(0, page));
        flash.addAttribute("size", Math.max(1, Math.min(100, size)));
        if (keyword != null && !keyword.isBlank()) flash.addAttribute("keyword", keyword.trim());
        if (rating != null) flash.addAttribute("rating", rating);
        if (status != null) flash.addAttribute("status", status.name());
        return "redirect:/admin/reviews";
    }
}
