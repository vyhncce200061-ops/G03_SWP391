package com.petshop.service;

import com.petshop.dto.catalog.BrandFormDto;
import com.petshop.entity.Brand;
import com.petshop.repository.BrandRepository;
import com.petshop.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandManageService {
    private final BrandRepository brands;
    private final ProductRepository products;
    private final EntityManager entityManager;
    private final Validator validator;

    @Transactional
    public Brand save(Integer id, BrandFormDto form, MultipartFile logoFile, boolean removeLogo) {
        if (form == null) throw new IllegalArgumentException("Thông tin thương hiệu không hợp lệ.");
        form.setName(form.getName() == null ? null : form.getName().trim());
        form.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        if (!validator.validate(form).isEmpty()) throw new IllegalArgumentException("Thông tin thương hiệu không hợp lệ.");
        Brand brand = id == null ? new Brand() : lock(id);
        boolean duplicate = id == null ? brands.existsByNameIgnoreCase(form.getName())
                : brands.existsByNameIgnoreCaseAndIdNot(form.getName(), id);
        if (duplicate) throw new IllegalArgumentException("Tên thương hiệu đã tồn tại.");
        boolean uploading = logoFile != null && !logoFile.isEmpty();
        if (uploading && removeLogo) throw new IllegalArgumentException("Logo mới và yêu cầu xóa logo không thể chọn cùng lúc.");
        BufferedImage image = uploading ? readLogo(logoFile) : null;
        String previous = brand.getLogoUrl();
        if (image != null) brand.setLogoUrl(storeLogo(image));
        else if (removeLogo) brand.setLogoUrl(null);
        if ((image != null || removeLogo) && previous != null) removeAfterCommit(previous);
        brand.setName(form.getName());
        brand.setDescription(form.getDescription());
        return brands.saveAndFlush(brand);
    }

    @Transactional
    public void toggleStatus(Integer id) {
        Brand brand = lock(id);
        brand.setIsActive(!Boolean.TRUE.equals(brand.getIsActive()));
        brands.saveAndFlush(brand);
    }

    @Transactional
    public void delete(Integer id) {
        Brand brand = lock(id);
        if (products.existsByBrand_Id(id)) {
            throw new IllegalArgumentException("Thương hiệu đang có sản phẩm sử dụng. Bạn có thể ngừng hoạt động thay vì xóa.");
        }
        brands.delete(brand);
        brands.flush();
        removeAfterCommit(brand.getLogoUrl());
    }

    private Brand lock(Integer id) {
        Brand brand = brands.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Thương hiệu không tồn tại."));
        entityManager.refresh(brand, LockModeType.PESSIMISTIC_WRITE);
        return brand;
    }

    private BufferedImage readLogo(MultipartFile file) {
        if (file.getSize() > 2L * 1024 * 1024) throw new IllegalArgumentException("Logo tối đa 2 MB.");
        String type = file.getContentType();
        if (type == null || !Set.of("image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/gif")
                .contains(type.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Logo cần có định dạng JPG, PNG hoặc GIF.");
        }
        try (var source = file.getInputStream(); var input = new MemoryCacheImageInputStream(source)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Logo cần là tệp hình ảnh hợp lệ.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!Set.of("jpeg", "jpg", "png", "gif").contains(reader.getFormatName().toLowerCase(Locale.ROOT))
                        || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) {
                    throw new IllegalArgumentException("Logo cần là ảnh JPG, PNG hoặc GIF, kích thước tối đa 4096 × 4096.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Logo cần là tệp hình ảnh hợp lệ.");
                return image;
            } finally { reader.dispose(); }
        } catch (IOException error) {
            throw new IllegalArgumentException("Logo không đọc được. Vui lòng chọn tệp ảnh hợp lệ.", error);
        }
    }

    private String storeLogo(BufferedImage image) {
        String name = "brand_" + UUID.randomUUID() + ".png";
        Path target = Path.of("uploads", "brands", name).toAbsolutePath().normalize();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) removeFile(target);
            }
        });
        try {
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(image, "png", target.toFile())) throw new IOException("PNG writer unavailable");
        } catch (IOException error) {
            throw new IllegalStateException("Không thể lưu logo thương hiệu. Vui lòng thử lại.", error);
        }
        return "/uploads/brands/" + name;
    }

    private void removeAfterCommit(String url) {
        if (url == null || !url.matches("/uploads/brands/brand_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")) return;
        Path target = Path.of(url.substring(1)).toAbsolutePath().normalize();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { removeFile(target); }
        });
    }

    private void removeFile(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException error) { log.warn("Could not remove unused brand logo {}", path, error); }
    }
}
