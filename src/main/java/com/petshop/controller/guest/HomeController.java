package com.petshop.controller.guest;

import com.petshop.service.CatalogService;
import com.petshop.repository.BrandRepository;
import com.petshop.entity.enums.ProductStatus;
import com.petshop.entity.enums.ProductVariantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CatalogService catalogService;
    private final BrandRepository brandRepository;

    @GetMapping({"/", "/home"})
    public String index(Model model) {
        model.addAttribute("featuredProducts", catalogService.getFeaturedProducts(8));
        model.addAttribute("categories", catalogService.getActiveCategoryTree());
        model.addAttribute("brands", brandRepository.findStorefrontBrands(ProductStatus.ACTIVE, ProductVariantStatus.ACTIVE));
        return "guest/home";
    }

    @GetMapping("/about")
    public String about() {
        return "guest/about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "guest/contact";
    }
}
