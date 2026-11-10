package com.petshop.controller.admin;

import com.petshop.dto.catalog.BrandFormDto;
import com.petshop.repository.BrandRepository;
import com.petshop.service.BrandManageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/brands")
@RequiredArgsConstructor
public class BrandManageController {
    private final BrandRepository brandRepository;
    private final BrandManageService brandService;

    @InitBinder("brandForm")
    public void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("name", "description", "logoFile", "removeLogo");
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String listBrands(Model model) {
        model.addAttribute("brandForm", new BrandFormDto());
        return view(model, null, false);
    }

    @PostMapping("/create")
    public String createBrand(@Valid @ModelAttribute("brandForm") BrandFormDto form,
                              BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasFieldErrors("name") && brandRepository.existsByNameIgnoreCase(form.getName())) {
            errors.rejectValue("name", "brand.duplicate", "Tên thương hiệu đã tồn tại.");
        }
        if (errors.hasErrors()) return view(model, null, true);
        return save(form, errors, model, redirect, null);
    }

    @PostMapping("/update/{id}")
    public String updateBrand(@PathVariable Integer id, @Valid @ModelAttribute("brandForm") BrandFormDto form,
                              BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!brandRepository.existsById(id)) {
            redirect.addFlashAttribute("errorMessage", "Thương hiệu không tồn tại.");
            return "redirect:/admin/brands";
        }
        if (!errors.hasFieldErrors("name") && brandRepository.existsByNameIgnoreCaseAndIdNot(form.getName(), id)) {
            errors.rejectValue("name", "brand.duplicate", "Tên thương hiệu đã tồn tại.");
        }
        if (errors.hasErrors()) return view(model, id, true);
        return save(form, errors, model, redirect, id);
    }

    private String save(BrandFormDto form, BindingResult errors, Model model, RedirectAttributes redirect, Integer id) {
        try {
            brandService.save(id, form, form.getLogoFile(), form.isRemoveLogo());
        } catch (DataIntegrityViolationException ex) {
            errors.rejectValue("name", "brand.conflict", "Không thể lưu thương hiệu. Vui lòng kiểm tra tên trùng và thử lại.");
            return view(model, id, true);
        } catch (IllegalArgumentException ex) {
            errors.rejectValue(ex.getMessage().startsWith("Logo") ? "logoFile" : "name", "brand.invalid", ex.getMessage());
            return view(model, id, true);
        } catch (IllegalStateException ex) {
            model.addAttribute("errorMessage", "Không thể lưu logo thương hiệu. Vui lòng thử lại.");
            return view(model, id, true);
        }
        redirect.addFlashAttribute("successMessage", id == null ? "Thêm thương hiệu thành công." : "Cập nhật thương hiệu thành công.");
        return "redirect:/admin/brands";
    }

    private String view(Model model, Integer id, boolean open) {
        model.addAttribute("brands", brandRepository.findAllByOrderByNameAsc());
        model.addAttribute("editingBrandId", id);
        model.addAttribute("brandModalOpen", open);
        model.addAttribute("currentLogoUrl", id == null ? null : brandRepository.findById(id).map(b -> b.getLogoUrl()).orElse(null));
        return "admin/brand-list";
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String oversizedLogo(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "Logo vượt dung lượng tải lên. Vui lòng chọn ảnh tối đa 2 MB.");
        return "redirect:/admin/brands";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleBrandStatus(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            brandService.toggleStatus(id);
            redirect.addFlashAttribute("successMessage", "Cập nhật trạng thái thương hiệu thành công.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/brands";
    }

    @PostMapping("/delete/{id}")
    public String deleteBrand(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            brandService.delete(id);
            redirect.addFlashAttribute("successMessage", "Xóa thương hiệu thành công.");
        } catch (DataIntegrityViolationException ex) {
            redirect.addFlashAttribute("errorMessage", "Không thể xóa thương hiệu đang được dữ liệu khác sử dụng.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/brands";
    }
}
