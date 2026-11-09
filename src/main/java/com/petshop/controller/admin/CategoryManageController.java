package com.petshop.controller.admin;

import com.petshop.dto.catalog.CategoryFormDto;
import com.petshop.service.CategoryManageService;
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
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryManageController {
    private final CategoryManageService categories;

    @InitBinder("categoryForm")
    public void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("code", "name", "description", "parentId", "sortOrder", "isActive");
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categories.listCategories());
        return "admin/category-list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("categoryForm", new CategoryFormDto());
        return form(model, null);
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("categoryForm", categories.editForm(id));
            return form(model, id);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/categories";
        }
    }

    @PostMapping("/create")
    public String createCategory(@Valid @ModelAttribute("categoryForm") CategoryFormDto dto,
                                 BindingResult errors, Model model, RedirectAttributes redirect) {
        return save(null, dto, errors, model, redirect);
    }

    @PostMapping("/update/{id}")
    public String updateCategory(@PathVariable Integer id,
                                 @Valid @ModelAttribute("categoryForm") CategoryFormDto dto,
                                 BindingResult errors, Model model, RedirectAttributes redirect) {
        try {
            categories.editForm(id);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/categories";
        }
        return save(id, dto, errors, model, redirect);
    }

    private String save(Integer id, CategoryFormDto dto, BindingResult errors, Model model,
                        RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, id);
        try {
            categories.save(id, dto);
        } catch (IllegalArgumentException ex) {
            errors.reject("category.invalid", ex.getMessage());
            return form(model, id);
        } catch (DataIntegrityViolationException ex) {
            errors.reject("category.conflict", "Không thể lưu. Mã có thể đã tồn tại hoặc danh mục cha vừa thay đổi. Vui lòng kiểm tra lại.");
            return form(model, id);
        }
        redirect.addFlashAttribute("successMessage", id == null ? "Thêm danh mục thành công." : "Cập nhật danh mục thành công.");
        return "redirect:/admin/categories";
    }

    private String form(Model model, Integer id) {
        model.addAttribute("categoryId", id);
        model.addAttribute("parentCategories", categories.parentOptions(id));
        model.addAttribute("parentLocked", categories.hasChildren(id));
        return "admin/category-form";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleCategoryStatus(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            categories.toggleStatus(id);
            redirect.addFlashAttribute("successMessage", "Cập nhật trạng thái danh mục thành công.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/delete/{id}")
    public String deleteCategory(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            categories.deactivate(id);
            redirect.addFlashAttribute("successMessage", "Danh mục đã ngừng hoạt động.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
