package com.petshop.controller.admin;

import com.petshop.dto.catalog.BrandFormDto;
import com.petshop.entity.Brand;
import com.petshop.repository.BrandRepository;
import com.petshop.repository.ProductRepository;
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
    private final ProductRepository productRepository;

    @InitBinder("brandForm")
    public void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("name", "description");
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
        Brand brand = Brand.builder().name(form.getName()).description(form.getDescription()).isActive(true).build();
        return save(brand, errors, model, redirect, null);
    }

    @PostMapping("/update/{id}")
    public String updateBrand(@PathVariable Integer id, @Valid @ModelAttribute("brandForm") BrandFormDto form,
                              BindingResult errors, Model model, RedirectAttributes redirect) {
        Brand brand = brandRepository.findById(id).orElse(null);
        if (brand == null) {
            redirect.addFlashAttribute("errorMessage", "Thương hiệu không tồn tại.");
            return "redirect:/admin/brands";
        }
        if (!errors.hasFieldErrors("name") && brandRepository.existsByNameIgnoreCaseAndIdNot(form.getName(), id)) {
            errors.rejectValue("name", "brand.duplicate", "Tên thương hiệu đã tồn tại.");
        }
        if (errors.hasErrors()) return view(model, id, true);
        brand.setName(form.getName());
        brand.setDescription(form.getDescription());
        // Logo is no longer edited here; preserve any legacy value and the current status.
        return save(brand, errors, model, redirect, id);
    }

    private String save(Brand brand, BindingResult errors, Model model, RedirectAttributes redirect, Integer id) {
        try {
            brandRepository.saveAndFlush(brand);
        } catch (DataIntegrityViolationException ex) {
            errors.rejectValue("name", "brand.conflict", "Không thể lưu thương hiệu. Vui lòng kiểm tra tên trùng và thử lại.");
            return view(model, id, true);
        }
        redirect.addFlashAttribute("successMessage", id == null ? "Thêm thương hiệu thành công." : "Cập nhật thương hiệu thành công.");
        return "redirect:/admin/brands";
    }

    private String view(Model model, Integer id, boolean open) {
        model.addAttribute("brands", brandRepository.findAllByOrderByNameAsc());
        model.addAttribute("editingBrandId", id);
        model.addAttribute("brandModalOpen", open);
        return "admin/brand-list";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleBrandStatus(@PathVariable Integer id, RedirectAttributes redirect) {
        Brand brand = brandRepository.findById(id).orElse(null);
        if (brand == null) {
            redirect.addFlashAttribute("errorMessage", "Thương hiệu không tồn tại.");
        } else {
            brand.setIsActive(!Boolean.TRUE.equals(brand.getIsActive()));
            brandRepository.save(brand);
            redirect.addFlashAttribute("successMessage", "Cập nhật trạng thái thương hiệu thành công.");
        }
        return "redirect:/admin/brands";
    }

    @PostMapping("/delete/{id}")
    public String deleteBrand(@PathVariable Integer id, RedirectAttributes redirect) {
        Brand brand = brandRepository.findById(id).orElse(null);
        if (brand == null) {
            redirect.addFlashAttribute("errorMessage", "Thương hiệu không tồn tại.");
            return "redirect:/admin/brands";
        }
        if (productRepository.existsByBrand_Id(id)) {
            redirect.addFlashAttribute("errorMessage", "Thương hiệu đang có sản phẩm sử dụng. Bạn có thể ngừng hoạt động thay vì xóa.");
            return "redirect:/admin/brands";
        }
        try {
            brandRepository.delete(brand);
            redirect.addFlashAttribute("successMessage", "Xóa thương hiệu thành công.");
        } catch (DataIntegrityViolationException ex) {
            redirect.addFlashAttribute("errorMessage", "Không thể xóa thương hiệu đang được dữ liệu khác sử dụng.");
        }
        return "redirect:/admin/brands";
    }
}
