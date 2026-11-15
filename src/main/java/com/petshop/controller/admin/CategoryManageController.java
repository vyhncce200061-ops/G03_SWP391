package com.petshop.controller.admin;

import com.petshop.dto.catalog.CategoryFormDto;
import com.petshop.dto.catalog.CategoryViewDto;
import com.petshop.service.CategoryManageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
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
    public String listCategories(@RequestParam(required = false) Integer expanded, Model model) {
        var roots = categories.listTree();
        model.addAttribute("categories", roots);
        model.addAttribute("childCount", roots.stream().mapToInt(c -> c.getChildren().size()).sum());
        model.addAttribute("expandedParentId", roots.stream().filter(c -> c.getId().equals(expanded))
                .map(CategoryViewDto::getId).findFirst().orElse(null));
        return "admin/category-list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("categoryForm", new CategoryFormDto());
        return form(model, null, null);
    }

    @GetMapping("/{parentId}/children/create")
    public String showChildForm(@PathVariable Integer parentId, Model model, RedirectAttributes redirect) {
        try {
            CategoryViewDto parent = categories.parentForCreation(parentId);
            CategoryFormDto dto = new CategoryFormDto();
            dto.setParentId(parentId);
            model.addAttribute("categoryForm", dto);
            return form(model, null, parent);
        } catch (IllegalArgumentException ex) {
            return missing(redirect, ex);
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("categoryForm", categories.editForm(id));
            return form(model, id, null);
        } catch (IllegalArgumentException ex) {
            return missing(redirect, ex);
        }
    }

    @PostMapping("/create")
    public String createCategory(@Valid @ModelAttribute("categoryForm") CategoryFormDto dto,
                                 BindingResult errors, Model model, RedirectAttributes redirect) {
        if (dto.getParentId() != null) {
            errors.reject("category.rootOnly", "Đây là form thêm danh mục cha. Để thêm danh mục con, hãy dùng nút bên trong nhóm cha.");
        }
        return save(null, dto, errors, model, redirect, null);
    }

    @PostMapping("/{parentId}/children/create")
    public String createChild(@PathVariable Integer parentId,
                              @Valid @ModelAttribute("categoryForm") CategoryFormDto dto,
                              BindingResult errors, Model model, RedirectAttributes redirect) {
        try {
            CategoryViewDto parent = categories.parentForCreation(parentId);
            // The containing group determines the parent; posted fields cannot move this new child.
            dto.setParentId(parentId);
            return save(null, dto, errors, model, redirect, parent);
        } catch (IllegalArgumentException ex) {
            return missing(redirect, ex);
        }
    }

    @PostMapping("/update/{id}")
    public String updateCategory(@PathVariable Integer id,
                                 @Valid @ModelAttribute("categoryForm") CategoryFormDto dto,
                                 BindingResult errors, Model model, RedirectAttributes redirect) {
        try {
            categories.editForm(id);
        } catch (IllegalArgumentException ex) {
            return missing(redirect, ex);
        }
        if (categories.isParent(id) && dto.getParentId() != null) {
            errors.reject("category.rootCannotHaveParent", "Danh mục cha là cấp cao nhất, không thể thêm danh mục cha mà chỉ có thể thêm danh mục con.");
        }
        return save(id, dto, errors, model, redirect, null);
    }

    private String save(Integer id, CategoryFormDto dto, BindingResult errors, Model model,
                        RedirectAttributes redirect, CategoryViewDto creationParent) {
        if (errors.hasErrors()) return form(model, id, creationParent);
        Integer savedId;
        try {
            savedId = categories.save(id, dto);
        } catch (IllegalArgumentException ex) {
            errors.reject("category.invalid", ex.getMessage());
            return form(model, id, creationParent);
        } catch (DataIntegrityViolationException ex) {
            errors.reject("category.conflict", "Không thể lưu. Mã có thể đã tồn tại hoặc danh mục cha vừa thay đổi. Vui lòng kiểm tra lại.");
            return form(model, id, creationParent);
        }
        redirect.addFlashAttribute("successMessage", id == null ? "Thêm danh mục thành công." : "Cập nhật danh mục thành công.");
        return "redirect:" + branchUrl(dto.getParentId() == null ? savedId : dto.getParentId());
    }

    private String form(Model model, Integer id, CategoryViewDto creationParent) {
        boolean isParent = id != null && categories.isParent(id);
        boolean parentLocked = isParent || categories.hasChildren(id);
        model.addAttribute("categoryId", id);
        model.addAttribute("creationParent", creationParent);
        model.addAttribute("isParentCategory", isParent);
        model.addAttribute("parentCategories", id == null ? java.util.List.of() : categories.parentOptions(id));
        model.addAttribute("parentLocked", parentLocked);
        model.addAttribute("categoryFormTitle", id != null ? "Chỉnh sửa danh mục"
                : creationParent == null ? "Thêm danh mục cha" : "Thêm danh mục con");
        model.addAttribute("categoryFormAction", id != null ? "/admin/categories/update/" + id
                : creationParent == null ? "/admin/categories/create" : "/admin/categories/" + creationParent.getId() + "/children/create");
        Integer branchId = creationParent == null ? null : creationParent.getId();
        if (id != null) {
            Integer oldParent = categories.editForm(id).getParentId();
            branchId = oldParent == null ? id : oldParent;
        }
        model.addAttribute("categoryBackUrl", branchId == null ? "/admin/categories" : branchUrl(branchId));
        return "admin/category-form";
    }

    private String branchUrl(Integer parentId) {
        return "/admin/categories?expanded=" + parentId + "#category-" + parentId;
    }

    private String missing(RedirectAttributes redirect, IllegalArgumentException ex) {
        redirect.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/admin/categories";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleCategoryStatus(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            Integer branchId = categories.toggleStatus(id);
            redirect.addFlashAttribute("successMessage", "Cập nhật trạng thái danh mục thành công.");
            return "redirect:" + branchUrl(branchId);
        } catch (IllegalArgumentException ex) {
            return missing(redirect, ex);
        } catch (TransientDataAccessException ex) {
            return busy(redirect);
        }
    }

    @PostMapping("/deactivate/{id}")
    public String deactivateCategory(@PathVariable Integer id, RedirectAttributes redirect) {
        return setActive(id, false, redirect);
    }

    @PostMapping("/activate/{id}")
    public String activateCategory(@PathVariable Integer id, RedirectAttributes redirect) {
        return setActive(id, true, redirect);
    }

    private String setActive(Integer id, boolean active, RedirectAttributes redirect) {
        try {
            Integer branchId = categories.setActive(id, active);
            redirect.addFlashAttribute("successMessage", active ? "Đã kích hoạt danh mục." : "Đã ngừng hoạt động danh mục.");
            return "redirect:" + branchUrl(branchId);
        } catch (IllegalArgumentException ex) {
            return missing(redirect, ex);
        } catch (TransientDataAccessException ex) {
            return busy(redirect);
        }
    }

    private String busy(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "Danh mục đang được cập nhật bởi thao tác khác. Vui lòng tải lại danh sách rồi thử lại.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/delete/{id}")
    public String deleteCategory(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            Integer parentId = categories.delete(id);
            redirect.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn danh mục.");
            return "redirect:" + (parentId == null ? "/admin/categories" : branchUrl(parentId));
        } catch (IllegalArgumentException ex) {
            return missing(redirect, ex);
        } catch (DataIntegrityViolationException ex) {
            redirect.addFlashAttribute("errorMessage", "Danh mục đang được dữ liệu khác sử dụng nên không thể xóa. Vui lòng tải lại danh sách; bạn có thể ngừng hoạt động thay vì xóa.");
            return "redirect:/admin/categories";
        } catch (TransientDataAccessException ex) {
            return busy(redirect);
        }
    }
}
