package com.petshop.controller.staff;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.entity.User;
import com.petshop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.petshop.dto.staff.StaffActivityDto;
import com.petshop.entity.OrderStatusHistory;
import com.petshop.entity.StockImport;
import com.petshop.repository.OrderStatusHistoryRepository;
import com.petshop.repository.StockImportRepository;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Staff Profile Controller (UC-17.1, UC-17.2, UC-17.3).
 * Handles view profile, update profile, change password, and performance statistics
 * for Staff/Admin users within the admin portal layout.
 */
@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffProfileController {

    private final UserService userService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final StockImportRepository stockImportRepository;

    /**
     * UC-17.1: Staff View Profile & Performance Statistics
     * Displays the staff member's profile page with personal info, change password form,
     * and activity statistics (processed orders count, stock imports count, recent audit actions).
     */
    @GetMapping("/profile")
    public String viewProfile(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User user = userService.findById(userDetails.getId());
        model.addAttribute("user", user);

        // 1. Thống kê số liệu hoạt động thực tế
        long processedOrdersCount = orderStatusHistoryRepository.countDistinctOrdersByStaffId(user.getId());
        long stockImportsCount = stockImportRepository.countByCreatedById(user.getId());
        long totalActionsCount = orderStatusHistoryRepository.countByChangedById(user.getId()) + stockImportsCount;

        model.addAttribute("processedOrdersCount", processedOrdersCount);
        model.addAttribute("stockImportsCount", stockImportsCount);
        model.addAttribute("totalActionsCount", totalActionsCount);

        // 2. Danh sách hoạt động gần đây (Recent Activities Feed)
        List<StaffActivityDto> recentActivities = new ArrayList<>();

        // 2.1 Các lần cập nhật trạng thái đơn hàng gần nhất
        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findRecentActivitiesByStaffId(
                user.getId(), PageRequest.of(0, 5));
        for (OrderStatusHistory h : histories) {
            String title = "Cập nhật đơn hàng";
            String badge = "bg-primary-subtle text-primary border border-primary-subtle";
            String icon = "bi-arrow-repeat";

            if (h.getNewStatus() != null) {
                switch (h.getNewStatus()) {
                    case CONFIRMED -> {
                        title = "Xác nhận đơn hàng";
                        badge = "bg-info-subtle text-info-emphasis border border-info-subtle";
                        icon = "bi-check2-circle";
                    }
                    case PREPARING -> {
                        title = "Đóng gói hàng hóa";
                        badge = "bg-warning-subtle text-warning-emphasis border border-warning-subtle";
                        icon = "bi-box-seam";
                    }
                    case SHIPPING -> {
                        title = "Bàn giao vận chuyển";
                        badge = "bg-primary-subtle text-primary border border-primary-subtle";
                        icon = "bi-truck";
                    }
                    case COMPLETED -> {
                        title = "Hoàn tất đơn hàng";
                        badge = "bg-success-subtle text-success border border-success-subtle";
                        icon = "bi-check-all";
                    }
                    case CANCELLED -> {
                        title = "Từ chối / Hủy đơn";
                        badge = "bg-danger-subtle text-danger border border-danger-subtle";
                        icon = "bi-x-circle";
                    }
                    default -> {}
                }
            }

            recentActivities.add(StaffActivityDto.builder()
                    .actionType("ORDER")
                    .referenceCode(h.getOrder() != null ? h.getOrder().getOrderCode() : ("#" + h.getId()))
                    .title(title)
                    .detail(h.getReason() != null && !h.getReason().isBlank() ? h.getReason() : ("Chuyển trạng thái sang " + h.getNewStatus()))
                    .badgeClass(badge)
                    .iconClass(icon)
                    .targetUrl(h.getOrder() != null ? ("/staff/orders/" + h.getOrder().getId()) : "/staff/orders")
                    .timestamp(h.getChangedAt())
                    .build());
        }

        // 2.2 Các phiếu nhập kho gần nhất
        List<StockImport> imports = stockImportRepository.findRecentImportsByStaffId(
                user.getId(), PageRequest.of(0, 5));
        for (StockImport imp : imports) {
            recentActivities.add(StaffActivityDto.builder()
                    .actionType("STOCK_IMPORT")
                    .referenceCode(imp.getImportCode() != null ? imp.getImportCode() : ("#IMP-" + imp.getId()))
                    .title("Nhập kho hàng hóa")
                    .detail("Nhà cung cấp: " + (imp.getSupplierName() != null ? imp.getSupplierName() : "Nhà phân phối"))
                    .badgeClass("bg-teal-subtle text-teal border border-teal-subtle")
                    .iconClass("bi-box-arrow-in-down")
                    .targetUrl("/staff/stock-imports/" + imp.getId())
                    .timestamp(imp.getCreatedAt() != null ? imp.getCreatedAt() : imp.getImportedAt())
                    .build());
        }

        // Sắp xếp các hoạt động theo thời gian giảm dần và lấy tối đa 6 mục
        recentActivities.sort(Comparator.comparing(StaffActivityDto::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())));
        if (recentActivities.size() > 6) {
            recentActivities = recentActivities.subList(0, 6);
        }

        model.addAttribute("recentActivities", recentActivities);

        return "staff/profile";
    }

    /**
     * UC-17.2: Staff Update Profile
     * Processes staff profile update (full name, phone number, avatar).
     */
    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @Valid @ModelAttribute ProfileUpdateRequestDto request,
                                BindingResult bindingResult,
                                @RequestParam(value = "avatarFile", required = false) org.springframework.web.multipart.MultipartFile avatarFile,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/staff/profile";
        }

        try {
            userService.updateProfile(userDetails.getId(), request);
            if (avatarFile != null && !avatarFile.isEmpty()) {
                userService.updateAvatar(userDetails.getId(), avatarFile);
            }
            refreshPrincipal(userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin cá nhân thành công!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/staff/profile";
    }

    /**
     * UC-17.3: Staff Change Password
     * Processes password change for staff member.
     */
    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute PasswordChangeRequestDto request,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/staff/profile";
        }
        try {
            userService.changePassword(userDetails.getId(), request);
            refreshPrincipal(userDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/profile";
    }

    private void refreshPrincipal(Long userId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            User updated = userService.findById(userId);
            CustomUserDetails newDetails = new CustomUserDetails(updated);
            var newAuth = new UsernamePasswordAuthenticationToken(
                    newDetails, newDetails.getPassword(), newDetails.getAuthorities());
            newAuth.setDetails(auth.getDetails());
            SecurityContextHolder.getContext().setAuthentication(newAuth);
        }
    }
}
