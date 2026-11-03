
package com.petshop.controller.admin;

import com.petshop.entity.Category;
import com.petshop.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryManageController {

    private final CategoryRepository categoryRepository;

    // Display all categories
    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        return "admin/category-list";
    }

    // Display the form to create a category
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("category", new Category());
        model.addAttribute("categories", categoryRepository.findAll());
        return "admin/category-form";
    }

    // Save a new category
    @PostMapping("/create")
    public String createCategory(
            @ModelAttribute Category category,
            RedirectAttributes redirectAttributes) {

        String code = category.getCode() == null
                ? "" : category.getCode().trim();

        String name = category.getName() == null
                ? "" : category.getName().trim();

        if (code.isEmpty() || name.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code and name are required."
            );
            return "redirect:/admin/categories/create";
        }

        if (categoryRepository.existsByCode(code)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code already exists."
            );
            return "redirect:/admin/categories/create";
        }

        category.setCode(code);
        category.setName(name);
        category.setId(null);
        try {
            normalizeCategory(category, null);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/categories/create";
        }

        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        if (category.getIsActive() == null) {
            category.setIsActive(true);
        }

        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Category created successfully."
        );

        return "redirect:/admin/categories";
    }

    // Display the form to edit a category
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Integer id,
            Model model,
            RedirectAttributes redirectAttributes) {

        Optional<Category> optionalCategory =
                categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category not found."
            );
            return "redirect:/admin/categories";
        }

        model.addAttribute("category", optionalCategory.get());
        model.addAttribute("categories", categoryRepository.findAll());

        return "admin/category-form";
    }

    // Update an existing category
    @PostMapping("/update/{id}")
    public String updateCategory(
            @PathVariable Integer id,
            @ModelAttribute Category formCategory,
            RedirectAttributes redirectAttributes) {

        Optional<Category> optionalCategory =
                categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category not found."
            );
            return "redirect:/admin/categories";
        }

        String code = formCategory.getCode() == null
                ? "" : formCategory.getCode().trim();

        String name = formCategory.getName() == null
                ? "" : formCategory.getName().trim();

        if (code.isEmpty() || name.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code and name are required."
            );
            return "redirect:/admin/categories/edit/" + id;
        }

        Optional<Category> duplicate =
                categoryRepository.findByCode(code);

        if (duplicate.isPresent()
                && !duplicate.get().getId().equals(id)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category code already exists."
            );
            return "redirect:/admin/categories/edit/" + id;
        }

        Category category = optionalCategory.get();

        formCategory.setCode(code);
        formCategory.setName(name);
        try {
            normalizeCategory(formCategory, id);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/categories/edit/" + id;
        }

        category.setCode(code);
        category.setName(name);
        category.setDescription(formCategory.getDescription());
        category.setSortOrder(
                formCategory.getSortOrder() == null
                        ? 0 : formCategory.getSortOrder()
        );
        category.setParentCategory(formCategory.getParentCategory());
        category.setIsActive(Boolean.TRUE.equals(formCategory.getIsActive()));

        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Category updated successfully."
        );

        return "redirect:/admin/categories";
    }

    private void normalizeCategory(Category form, Integer id) {
        if (form.getCode().length() > 50 || form.getName().length() > 150
                || (form.getDescription() != null && form.getDescription().length() > 1000)
                || (form.getSortOrder() != null && form.getSortOrder() < 0)) {
            throw new IllegalArgumentException("Mã tối đa 50 ký tự, tên tối đa 150, mô tả tối đa 1000; thứ tự không được âm.");
        }
        Integer parentId = form.getParentCategory() == null ? null : form.getParentCategory().getId();
        if (parentId == null) {
            form.setParentCategory(null);
            return;
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục cha không tồn tại."));
        if (parentId.equals(id) || parent.getParentCategory() != null
                || (id != null && categoryRepository.existsByParentCategory_Id(id))) {
            throw new IllegalArgumentException("Danh mục chỉ có hai cấp; không được tự làm cha hoặc tạo vòng lặp.");
        }
        form.setParentCategory(parent);
    }

    // Activate or deactivate a category
    @PostMapping("/toggle-status/{id}")
    public String toggleCategoryStatus(
            @PathVariable Integer id,
            RedirectAttributes redirectAttributes) {

        Optional<Category> optionalCategory =
                categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category not found."
            );
            return "redirect:/admin/categories";
        }

        Category category = optionalCategory.get();
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Category status updated successfully."
        );

        return "redirect:/admin/categories";
    }

    // Soft delete: deactivate the category without deleting database data
    @PostMapping("/delete/{id}")
    public String deleteCategory(
            @PathVariable Integer id,
            RedirectAttributes redirectAttributes) {

        Optional<Category> optionalCategory =
                categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Category not found."
            );
            return "redirect:/admin/categories";
        }

        Category category = optionalCategory.get();

        if (Boolean.FALSE.equals(category.getIsActive())) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "This category is already inactive."
            );
            return "redirect:/admin/categories";
        }

        category.setIsActive(false);
        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Category deactivated successfully."
        );

        return "redirect:/admin/categories";
    }
}
