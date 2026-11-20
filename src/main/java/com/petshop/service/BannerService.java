package com.petshop.service;

import com.petshop.dto.banner.BannerFormDto;
import com.petshop.entity.Banner;
import com.petshop.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Business rules for homepage banners (CRUD, scheduling, image upload). */
@Service
@RequiredArgsConstructor
@Slf4j
public class BannerService {
    /** Maximum number of banners shown in the homepage carousel. */
    public static final int MAX_DISPLAYED = 8;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_SIDE = 4096;
    static final int MIN_WIDTH = 800;
    static final double MIN_RATIO = 1.5;
    static final double MAX_RATIO = 4.0;
    private static final String URL_PREFIX = "/uploads/banners/";
    private static final java.util.regex.Pattern OWN_FILE = java.util.regex.Pattern.compile(
            "/uploads/banners/banner_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)");

    private final BannerRepository banners;

    @Transactional(readOnly = true)
    public List<Banner> listAll() {
        return banners.findAllByIsDeletedFalseOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Banner get(Integer id) {
        return banners.findByIdAndIsDeletedFalse(id).orElseThrow(() -> new IllegalArgumentException("Banner không tồn tại."));
    }

    /**
     * Banners visible to shoppers right now: switched on, inside schedule, ordered
     * by sortOrder.
     */
    @Transactional(readOnly = true)
    public List<Banner> displayable() {
        return banners.findDisplayable(LocalDateTime.now(), PageRequest.of(0, MAX_DISPLAYED));
    }

    @Transactional
    public Banner save(Integer id, BannerFormDto form) {
        if (form == null)
            throw new IllegalArgumentException("Thông tin banner không hợp lệ.");
        String title = clean(form.getTitle());
        if (title == null)
            throw new IllegalArgumentException("Vui lòng nhập tiêu đề banner.");
        if (title.length() > 150)
            throw new IllegalArgumentException("Tiêu đề tối đa 150 ký tự.");
        String subtitle = clean(form.getSubtitle());
        if (subtitle != null && subtitle.length() > 300)
            throw new IllegalArgumentException("Mô tả ngắn tối đa 300 ký tự.");
        String buttonText = "Khám phá";
        String link = validateLink(clean(form.getLinkUrl()));
        Integer sort = form.getSortOrder();
        if (sort == null || sort < 0 || sort > 9999)
            throw new IllegalArgumentException("Thứ tự hiển thị từ 0 đến 9999.");
        LocalDateTime start = form.getStartAt();
        LocalDateTime end = form.getEndAt();
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        if (end != null && !end.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải ở tương lai.");
        }

        Banner banner = id == null ? new Banner() : get(id);
        MultipartFile file = form.getImageFile();
        boolean uploading = file != null && !file.isEmpty();
        if (id == null && !uploading)
            throw new IllegalArgumentException("Vui lòng chọn ảnh banner.");
        String previous = banner.getImageUrl();
        if (uploading)
            banner.setImageUrl(storeImage(readImage(file)));

        banner.setTitle(title);
        banner.setSubtitle(subtitle);
        banner.setLinkUrl(link);
        banner.setButtonText(buttonText);
        banner.setSortOrder(sort);
        banner.setIsActive(form.isActive());
        banner.setStartAt(start);
        banner.setEndAt(end);
        Banner saved = banners.saveAndFlush(banner);
        resequenceBanners(saved.getId(), sort);
        if (uploading && previous != null)
            removeAfterCommit(previous);
        return get(saved.getId());
    }

    @Transactional
    public boolean toggle(Integer id) {
        Banner banner = get(id);
        banner.setIsActive(!Boolean.TRUE.equals(banner.getIsActive()));
        banners.saveAndFlush(banner);
        return banner.getIsActive();
    }

    @Transactional
    public void delete(Integer id) {
        Banner banner = get(id);
        banner.setIsDeleted(true);
        banner.setDeletedAt(LocalDateTime.now());
        banners.saveAndFlush(banner);
        resequenceAll();
    }

    private void resequenceAll() {
        List<Banner> all = banners.findAllByIsDeletedFalseOrderBySortOrderAscIdAsc();
        for (int i = 0; i < all.size(); i++) {
            all.get(i).setSortOrder(i + 1);
        }
        banners.saveAllAndFlush(all);
    }

    private void resequenceBanners(Integer currentId, Integer requestedSort) {
        List<Banner> all = banners.findAllByIsDeletedFalseOrderBySortOrderAscIdAsc();
        Banner current = all.stream().filter(b -> b.getId().equals(currentId)).findFirst().orElse(null);
        if (current == null)
            return;

        List<Banner> others = all.stream().filter(b -> !b.getId().equals(currentId)).toList();
        List<Banner> reordered = new java.util.ArrayList<>(others);

        int targetIndex;
        if (requestedSort == null || requestedSort <= 0) {
            targetIndex = 0; // Dẫn đầu
        } else if (requestedSort >= 9999 || requestedSort > reordered.size()) {
            targetIndex = reordered.size(); // Cuối cùng
        } else {
            targetIndex = requestedSort - 1; // 1-based index (Vị trí 1 -> index 0, Vị trí 2 -> index 1)
        }
        targetIndex = Math.max(0, Math.min(targetIndex, reordered.size()));
        reordered.add(targetIndex, current);

        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setSortOrder(i + 1);
        }
        banners.saveAllAndFlush(reordered);
    }

    /**
     * Only same-site paths ("/products?...") or absolute http(s) links are
     * accepted.
     */
    static String validateLink(String link) {
        if (link == null)
            return null;
        if (link.chars().anyMatch(Character::isWhitespace) || link.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Liên kết không được chứa khoảng trắng.");
        }
        boolean internal = link.startsWith("/") && !link.startsWith("//") && !link.contains("\\");
        String lower = link.toLowerCase(Locale.ROOT);
        boolean external = (lower.startsWith("http://") && link.length() > 7)
                || (lower.startsWith("https://") && link.length() > 8);
        if (!internal && !external) {
            throw new IllegalArgumentException("Liên kết phải bắt đầu bằng /, http:// hoặc https://.");
        }
        return link;
    }

    private record Img(BufferedImage image, String ext) {
    }

    private Img readImage(MultipartFile file) {
        if (file.getSize() > MAX_BYTES)
            throw new IllegalArgumentException("Ảnh banner tối đa 5 MB.");
        String type = file.getContentType();
        if (type == null || !Set.of("image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/gif")
                .contains(type.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Ảnh banner cần có định dạng JPG, PNG hoặc GIF.");
        }
        try (var source = file.getInputStream(); var input = new MemoryCacheImageInputStream(source)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                throw new IllegalArgumentException("Ảnh banner cần là tệp hình ảnh hợp lệ.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png", "gif").contains(format)) {
                    throw new IllegalArgumentException("Ảnh banner cần là ảnh JPG, PNG hoặc GIF.");
                }
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_SIDE || h > MAX_SIDE) {
                    throw new IllegalArgumentException("Kích thước ảnh tối đa " + MAX_SIDE + " × " + MAX_SIDE + " px.");
                }
                if (w < MIN_WIDTH) {
                    throw new IllegalArgumentException(
                            "Ảnh banner cần rộng tối thiểu " + MIN_WIDTH + " px (khuyến nghị 1600 × 500).");
                }
                double ratio = (double) w / h;
                if (ratio < MIN_RATIO || ratio > MAX_RATIO) {
                    throw new IllegalArgumentException(
                            "Tỉ lệ ảnh (rộng/cao) cần từ 1,5 đến 4 (khuyến nghị 1600 × 500).");
                }
                BufferedImage image = reader.read(0);
                if (image == null)
                    throw new IllegalArgumentException("Ảnh banner cần là tệp hình ảnh hợp lệ.");
                return new Img(image, format.equals("png") || format.equals("gif") ? "png" : "jpg");
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Không đọc được ảnh banner. Vui lòng chọn tệp ảnh hợp lệ.", error);
        }
    }

    private String storeImage(Img img) {
        String name = "banner_" + UUID.randomUUID() + "." + img.ext();
        Path target = Path.of("uploads", "banners", name).toAbsolutePath().normalize();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED)
                    removeFile(target);
            }
        });
        try {
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(img.image(), img.ext(), target.toFile()))
                throw new IOException("Image writer unavailable");
        } catch (IOException error) {
            throw new IllegalStateException("Không thể lưu ảnh banner. Vui lòng thử lại.", error);
        }
        return URL_PREFIX + name;
    }

    private void removeAfterCommit(String url) {
        if (url == null || !OWN_FILE.matcher(url).matches())
            return;
        Path target = Path.of(url.substring(1)).toAbsolutePath().normalize();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removeFile(target);
            }
        });
    }

    private void removeFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            log.warn("Could not remove unused banner image {}", path, error);
        }
    }

    private static String clean(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
