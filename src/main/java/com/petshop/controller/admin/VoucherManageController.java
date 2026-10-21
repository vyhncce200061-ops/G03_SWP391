package com.petshop.controller.admin;

import com.petshop.dto.voucher.VoucherRequestDto;
import com.petshop.entity.Voucher;
import com.petshop.entity.enums.VoucherStatus;
import com.petshop.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/vouchers")
@RequiredArgsConstructor
public class VoucherManageController {

    private final VoucherService voucherService;

    @GetMapping
    public String listVouchers(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) VoucherStatus status,
                               @RequestParam(defaultValue = "false") boolean deleted,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
        Page<Voucher> vouchers = voucherService.searchVouchers(keyword, status, deleted, pageable);
        model.addAttribute("vouchers", vouchers);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("deleted", deleted);
        return "admin/voucher-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        if (!model.containsAttribute("voucherForm")) {
            VoucherRequestDto dto = VoucherRequestDto.builder()
                    .totalUsageLimit(100)
                    .perCustomerLimit(1)
                    .status(VoucherStatus.ACTIVE)
                    .build();
            model.addAttribute("voucherForm", dto);
        }
        return "admin/voucher-form";
    }

    @PostMapping("/create")
    public String saveVoucher(@Valid @ModelAttribute("voucherForm") VoucherRequestDto voucherForm,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        if (bindingResult.hasErrors()) {
            return "admin/voucher-form";
        }

        try {
            voucherService.createVoucher(voucherForm);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo mã giảm giá mới thành công!");
            return "redirect:/admin/vouchers";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/voucher-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Voucher voucher;
        try {
            voucher = voucherService.getVoucherById(id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/vouchers";
        }
        VoucherRequestDto dto = VoucherRequestDto.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .title(voucher.getTitle())
                .description(voucher.getDescription())
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .minOrderAmount(voucher.getMinOrderAmount())
                .maxDiscountAmount(voucher.getMaxDiscountAmount())
                .startDate(voucher.getStartDate())
                .endDate(voucher.getEndDate())
                .totalUsageLimit(voucher.getTotalUsageLimit())
                .perCustomerLimit(voucher.getPerCustomerLimit())
                .status(voucher.getStatus())
                .build();
        model.addAttribute("voucherForm", dto);
        model.addAttribute("isEdit", true);
        return "admin/voucher-form";
    }

    @PostMapping("/{id}/edit")
    public String updateVoucher(@PathVariable Long id,
                                @Valid @ModelAttribute("voucherForm") VoucherRequestDto voucherForm,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "admin/voucher-form";
        }

        try {
            voucherService.updateVoucher(id, voucherForm);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật mã giảm giá thành công!");
            return "redirect:/admin/vouchers";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "admin/voucher-form";
        }
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            voucherService.toggleVoucherStatus(id);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái voucher thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/vouchers";
    }

    @PostMapping("/{id}/delete")
    public String deleteVoucher(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            voucherService.softDeleteVoucher(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa voucher. Lịch sử đơn hàng vẫn được giữ lại.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/vouchers";
    }

    @PostMapping("/{id}/restore")
    public String restoreVoucher(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            voucherService.restoreDeletedVoucher(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phục hồi voucher về danh sách quản lý. Hãy kiểm tra trạng thái, hạn dùng và lượt còn lại trước khi sử dụng.");
            return "redirect:/admin/vouchers";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/vouchers?deleted=true";
        }
    }
}
