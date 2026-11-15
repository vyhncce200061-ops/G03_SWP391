package com.petshop.controller.customer;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.entity.CustomerVoucher;
import com.petshop.entity.Voucher;
import com.petshop.repository.VoucherRepository;
import com.petshop.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/customer/vouchers")
@RequiredArgsConstructor
public class CustomerVoucherController {

    private final VoucherService voucherService;
    private final VoucherRepository voucherRepository;

    @GetMapping
    public String myVouchers(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        List<CustomerVoucher> walletVouchers = voucherService.getCustomerWalletVouchers(userDetails.getId());
        List<Voucher> publicVouchers = voucherRepository.findAvailablePublicVouchers(LocalDateTime.now());

        Set<Long> savedVoucherIds = walletVouchers.stream()
                .map(cv -> cv.getVoucher().getId())
                .collect(Collectors.toSet());

        model.addAttribute("walletVouchers", walletVouchers);
        model.addAttribute("publicVouchers", publicVouchers);
        model.addAttribute("savedVoucherIds", savedVoucherIds);
        return "customer/vouchers";
    }

    @PostMapping("/save/{voucherId}")
    public String saveVoucher(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @PathVariable Long voucherId,
                             RedirectAttributes redirectAttributes) {
        try {
            voucherService.saveVoucherToWallet(userDetails.getId(), voucherId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thu thập mã giảm giá vào ví thành công!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/vouchers";
    }

    @PostMapping("/delete/{walletId}")
    public String deleteVoucher(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @PathVariable Long walletId,
                               RedirectAttributes redirectAttributes) {
        voucherService.removeVoucherFromWallet(userDetails.getId(), walletId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa voucher khỏi ví của bạn.");
        return "redirect:/customer/vouchers";
    }
}
