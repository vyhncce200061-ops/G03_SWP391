package com.petshop.controller.admin;

import com.petshop.entity.Voucher;
import com.petshop.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/vouchers")
@RequiredArgsConstructor
public class VoucherManageController {

    private final VoucherService voucherService;

    @GetMapping
    public String listVouchers(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Voucher> vouchers = voucherService.getAllVouchers(pageable);
        model.addAttribute("vouchers", vouchers);
        return "admin/voucher-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        if (!model.containsAttribute("voucher")) {
            model.addAttribute("voucher", new Voucher());
        }
        return "admin/voucher-form";
    }

    @PostMapping("/create")
    public String saveVoucher(@ModelAttribute Voucher voucher,
                              RedirectAttributes redirectAttributes) {
        try {
            voucherService.createVoucher(voucher);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo mã giảm giá mới thành công!");
            return "redirect:/admin/vouchers";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/vouchers/create";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        // Find existing voucher
        Voucher voucher = voucherService.getAllVouchers(PageRequest.of(0, 100)).stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElse(null);
        model.addAttribute("voucher", voucher);
        return "admin/voucher-form";
    }

    @PostMapping("/{id}/edit")
    public String updateVoucher(@PathVariable Long id,
                                @ModelAttribute Voucher voucher,
                                RedirectAttributes redirectAttributes) {
        voucherService.updateVoucher(id, voucher);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật mã giảm giá thành công!");
        return "redirect:/admin/vouchers";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        voucherService.toggleVoucherStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái voucher thành công!");
        return "redirect:/admin/vouchers";
    }
}
