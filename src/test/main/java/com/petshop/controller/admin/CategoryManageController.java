package com.petshop.controller.admin;

import com.petshop.entity.Category;
import com.petshop.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryManageController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("rootCategories", categoryRepository.findByParentCategoryIsNullAndIsActiveTrueOrderBySortOrderAsc());
        if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", new Category());
        }
        return "admin/category-list";
    }

    @PostMapping("/create")
    public String createCategory(@ModelAttribute Category category,
                                 @RequestParam(required = false) Integer parentId,
                                 RedirectAttributes redirectAttributes) {
        if (parentId != null) {
            categoryRepository.findById(parentId).ifPresent(category::setParentCategory);
        }
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Thêm danh mục mới thành công!");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/edit")
    public String updateCategory(@PathVariable Integer id,
                                 @ModelAttribute Category categoryData,
                                 RedirectAttributes redirectAttributes) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục: " + id));

        category.setName(categoryData.getName());
        category.setCode(categoryData.getCode());
        category.setDescription(categoryData.getDescription());
        category.setSortOrder(categoryData.getSortOrder());
        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật danh mục thành công!");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục: " + id));
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái danh mục thành công!");
        return "redirect:/admin/categories";
    }
}
