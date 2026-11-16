package com.petshop.controller.admin;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.dto.admin.StaffCreateRequestDto;
import com.petshop.dto.admin.UserAdminUpdateDto;
import com.petshop.entity.User;
import com.petshop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserManageController {

    private final UserService userService;

    @InitBinder("userForm")
    public void bindForm(org.springframework.web.bind.WebDataBinder binder) {
        // The path identifies the record; no login identifiers, role or status are bindable.
        binder.setAllowedFields("fullName");
    }

    @ModelAttribute("currentAdminId")
    public Long currentAdminId(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getId();
    }

    @GetMapping
    public String listUsers(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String role,
                            @RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {
        Pageable pageable = com.petshop.util.PageRequests.bounded(page, size);
        Page<User> users = userService.getAllUsers(keyword, role, status, pageable);

        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        return "admin/user-list";
    }

    @GetMapping("/create")
    public String createStaffForm(Model model) {
        if (!model.containsAttribute("staffForm")) {
            model.addAttribute("staffForm", new StaffCreateRequestDto());
        }
        return "admin/user-form";
    }

    @PostMapping("/create")
    public String saveStaff(@Valid @ModelAttribute("staffForm") StaffCreateRequestDto staffForm,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        if (bindingResult.hasErrors()) {
            return "admin/user-form";
        }

        try {
            userService.createStaffAccount(staffForm);
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo Staff và gửi email nhận tài khoản. Nhân viên cần đặt mật khẩu qua liên kết trong 30 phút.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/user-form";
        }
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleUserStatus(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   @PathVariable Long id,
                                   @RequestParam(required = false) String targetStatus,
                                   RedirectAttributes redirectAttributes) {
        try {
            userService.setUserStatus(id, targetStatus, currentAdminId(userDetails));
            redirectAttributes.addFlashAttribute("successMessage", "LOCKED".equals(targetStatus) ? "Đã khóa tài khoản." : "Đã mở khóa tài khoản.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        model.addAttribute("targetUser", userService.findById(id));
        model.addAttribute("canEditStaff", false);
        return "admin/user-edit";
    }

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        if (!"STAFF".equals(user.getRole().getCode())) return "redirect:/admin/users/" + id;
        model.addAttribute("userForm", UserAdminUpdateDto.builder().fullName(user.getFullName()).build());
        model.addAttribute("targetUser", user);
        model.addAttribute("canEditStaff", true);
        return "admin/user-edit";
    }

    @PostMapping("/{id}/edit")
    public String updateUser(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @PathVariable Long id,
                             @Valid @ModelAttribute("userForm") UserAdminUpdateDto userForm,
                             BindingResult bindingResult,
                             jakarta.servlet.http.HttpServletRequest request,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        User targetUser = userService.findById(id);
        if (!"STAFF".equals(targetUser.getRole().getCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Admin chỉ được chỉnh sửa họ tên của Staff. Chủ tài khoản tự cập nhật hồ sơ cá nhân.");
            return "redirect:/admin/users/" + id;
        }
        model.addAttribute("targetUser", targetUser);
        model.addAttribute("canEditStaff", true);
        // Spring also supplies the URI variable "id" to the binder. Check submitted parameters
        // separately so the trusted path is accepted while a forged form ID is rejected.
        if (request.getParameterMap().keySet().stream().anyMatch(field -> !java.util.Set.of("fullName", "_csrf").contains(field))) {
            bindingResult.reject("restrictedFields", "Chỉ được cập nhật họ tên Staff. Vui lòng tải lại trang nếu đang dùng form cũ.");
        }
        if (bindingResult.hasErrors()) {
            return "admin/user-edit";
        }

        try {
            userService.updateUserByAdmin(id, userForm, currentAdminId(userDetails));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật họ tên nhân viên.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("targetUser", userService.findById(id));
            return "admin/user-edit";
        }
    }

    @PostMapping("/{id}/resend-invitation")
    public String resendInvitation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.resendStaffInvitation(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lại lời mời nhận tài khoản Staff. Liên kết trước đó đã hết hiệu lực.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public String conflictingData(RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage","Thông tin đã tồn tại hoặc không còn hợp lệ. Vui lòng tải lại danh sách và kiểm tra trước khi lưu.");
        return "redirect:/admin/users";
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public String invalidAccount(IllegalArgumentException error, RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage", error.getMessage());
        return "redirect:/admin/users";
    }
}
