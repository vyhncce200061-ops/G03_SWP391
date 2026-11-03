
package com.petshop.controller.admin;

import com.petshop.entity.Brand;
import com.petshop.repository.BrandRepository;
import com.petshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin/brands")
@RequiredArgsConstructor
public class BrandManageController {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    // Display all brands
    @GetMapping
    public String listBrands(Model model) {
        model.addAttribute(
                "brands",
                brandRepository.findAllByOrderByNameAsc()
        );
        model.addAttribute("brand", new Brand());
        return "admin/brand-list";
    }

    // Create a new brand
    @PostMapping("/create")
    public String createBrand(
            @ModelAttribute Brand brand,
            RedirectAttributes redirectAttributes) {

        String name = brand.getName() == null
                ? "" : brand.getName().trim();

        if (name.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Brand name is required."
            );
            return "redirect:/admin/brands";
        }

        if (brandRepository.existsByNameIgnoreCase(name)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Brand name already exists."
            );
            return "redirect:/admin/brands";
        }

        brand.setId(null);
        brand.setName(name);
        brand.setIsActive(true);

        brandRepository.save(brand);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Brand created successfully."
        );

        return "redirect:/admin/brands";
    }

    // Update an existing brand
    @PostMapping("/update/{id}")
    public String updateBrand(
            @PathVariable Integer id,
            @ModelAttribute Brand formBrand,
            RedirectAttributes redirectAttributes) {

        Optional<Brand> optionalBrand =
                brandRepository.findById(id);

        if (optionalBrand.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Brand not found."
            );
            return "redirect:/admin/brands";
        }

        String name = formBrand.getName() == null
                ? "" : formBrand.getName().trim();

        if (name.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Brand name is required."
            );
            return "redirect:/admin/brands";
        }

        if (brandRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Brand name already exists."
            );
            return "redirect:/admin/brands";
        }

        Brand brand = optionalBrand.get();
        brand.setName(name);
        brand.setDescription(formBrand.getDescription());
        brand.setLogoUrl(formBrand.getLogoUrl());

        brandRepository.save(brand);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Brand updated successfully."
        );

        return "redirect:/admin/brands";
    }

    // Activate or deactivate a brand
    @PostMapping("/toggle-status/{id}")
    public String toggleBrandStatus(
            @PathVariable Integer id,
            RedirectAttributes redirectAttributes) {

        Optional<Brand> optionalBrand =
                brandRepository.findById(id);

        if (optionalBrand.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Brand not found."
            );
            return "redirect:/admin/brands";
        }

        Brand brand = optionalBrand.get();
        brand.setIsActive(
                !Boolean.TRUE.equals(brand.getIsActive())
        );

        brandRepository.save(brand);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Brand status updated successfully."
        );

        return "redirect:/admin/brands";
    }

    // Delete a brand if no products use it
    @PostMapping("/delete/{id}")
    public String deleteBrand(
            @PathVariable Integer id,
            RedirectAttributes redirectAttributes) {

        Optional<Brand> optionalBrand =
                brandRepository.findById(id);

        if (optionalBrand.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Brand not found."
            );
            return "redirect:/admin/brands";
        }

        if (productRepository.existsByBrand_Id(id)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Cannot delete this brand because products are using it. Deactivate it instead."
            );
            return "redirect:/admin/brands";
        }

        try {
            brandRepository.delete(optionalBrand.get());

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Brand deleted successfully."
            );
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Cannot delete this brand because it is being used by other data."
            );
        }

        return "redirect:/admin/brands";
    }
}
