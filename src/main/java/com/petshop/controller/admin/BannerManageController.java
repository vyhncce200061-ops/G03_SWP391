package com.petshop.controller.admin;

import com.petshop.dto.banner.BannerFormDto;
import com.petshop.entity.Banner;
import com.petshop.entity.Category;
import com.petshop.repository.CategoryRepository;
import com.petshop.service.BannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/banners")
@RequiredArgsConstructor
public class BannerManageController {
    private final BannerService bannerService;
    private final CategoryRepository categoryRepository;

    public record LinkOption(String value, String label) {}
    public record PositionOption(int value, String label) {}

    @InitBinder("form")
    public void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("title", "subtitle", "linkUrl", "buttonText", "sortOrder", "active",
                "startAt", "endAt", "imageFile");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("banners", bannerService.listAll());
        model.addAttribute("maxDisplayed", BannerService.MAX_DISPLAYED);
        return "admin/banner-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        BannerFormDto form = new BannerFormDto();
        form.setSortOrder(9999);
        return form(model, form, null, null);
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") BannerFormDto form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return form(model, form, null, null);
        try {
            bannerService.save(null, form);
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return form(model, form, null, null);
        }
        redirect.addFlashAttribute("successMessage", "Thêm banner thành công.");
        return "redirect:/admin/banners";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model, RedirectAttributes redirect) {
        try {
            Banner banner = bannerService.get(id);
            return form(model, BannerFormDto.from(banner), id, banner.getImageUrl());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/banners";
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Integer id, @Valid @ModelAttribute("form") BannerFormDto form,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        String currentImage;
        try {
            currentImage = bannerService.get(id).getImageUrl();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/banners";
        }
        if (errors.hasErrors()) return form(model, form, id, currentImage);
        try {
            bannerService.save(id, form);
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return form(model, form, id, currentImage);
        }
        redirect.addFlashAttribute("successMessage", "Cập nhật banner thành công.");
        return "redirect:/admin/banners";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            boolean on = bannerService.toggle(id);
            redirect.addFlashAttribute("successMessage", on ? "Đã bật hiển thị banner." : "Đã ẩn banner.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/banners";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            bannerService.delete(id);
            redirect.addFlashAttribute("successMessage", "Đã xóa banner.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/banners";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String oversized(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "Ảnh vượt dung lượng tải lên. Vui lòng chọn ảnh tối đa 5 MB.");
        return "redirect:/admin/banners";
    }

    private String form(Model model, BannerFormDto form, Integer id, String currentImage) {
        model.addAttribute("form", form);
        model.addAttribute("bannerId", id);
        model.addAttribute("currentImage", currentImage);
        model.addAttribute("linkOptions", getLinkOptions(form.getLinkUrl()));
        model.addAttribute("positionOptions", getPositionOptions(id));
        return "admin/banner-form";
    }

    private List<PositionOption> getPositionOptions(Integer currentBannerId) {
        List<Banner> allBanners = bannerService.listAll();
        List<Banner> others = allBanners.stream()
                .filter(b -> currentBannerId == null || !b.getId().equals(currentBannerId))
                .toList();

        List<PositionOption> options = new ArrayList<>();
        if (others.isEmpty()) {
            options.add(new PositionOption(1, "Dẫn đầu (Vị trí 1)"));
            return options;
        }

        options.add(new PositionOption(0, "Dẫn đầu (Hiển thị đầu tiên)"));
        for (int i = 0; i < others.size(); i++) {
            Banner b = others.get(i);
            int pos = i + 1;
            String snippet = b.getTitle();
            if (snippet.length() > 30) {
                snippet = snippet.substring(0, 27) + "...";
            }
            options.add(new PositionOption(pos, "Vị trí " + pos + " (Hiện tại: " + snippet + ")"));
        }
        options.add(new PositionOption(9999, "Cuối cùng (Vị trí " + (others.size() + 1) + ")"));
        return options;
    }

    private List<LinkOption> getLinkOptions(String currentLinkUrl) {
        List<LinkOption> list = new ArrayList<>();
        list.add(new LinkOption("", "-- Không gắn liên kết --"));
        list.add(new LinkOption("/products", "Tất cả sản phẩm"));
        list.add(new LinkOption("/products?petType=DOG", "Sản phẩm cho Chó 🐶"));
        list.add(new LinkOption("/products?petType=CAT", "Sản phẩm cho Mèo 🐱"));

        try {
            List<Category> categories = categoryRepository.findStorefrontCategories();
            for (Category cat : categories) {
                String prefix = cat.getParentCategory() != null ? "  ↳ " : "Danh mục: ";
                list.add(new LinkOption("/products?categoryId=" + cat.getId(), prefix + cat.getName()));
            }
        } catch (Exception ignored) {
        }

        if (currentLinkUrl != null && !currentLinkUrl.isBlank() && list.stream().noneMatch(o -> o.value().equals(currentLinkUrl))) {
            list.add(new LinkOption(currentLinkUrl, "Liên kết hiện tại: " + currentLinkUrl));
        }

        return list;
    }
}
