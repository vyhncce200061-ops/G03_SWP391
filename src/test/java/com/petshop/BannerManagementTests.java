package com.petshop;

import com.petshop.dto.banner.BannerFormDto;
import com.petshop.entity.Banner;
import com.petshop.repository.BannerRepository;
import com.petshop.service.BannerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "vnpay.tmn-code=", "vnpay.hash-secret=", "vnpay.return-url=",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.datasource.url=jdbc:h2:mem:bannerTest;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
})
@ActiveProfiles("test")
@Transactional
class BannerManagementTests {

    @Autowired
    private BannerService bannerService;

    @Autowired
    private BannerRepository bannerRepository;

    private Banner createTestBanner(String title, int sortOrder, boolean active, LocalDateTime start, LocalDateTime end) {
        Banner b = Banner.builder()
                .title(title)
                .subtitle("Mô tả " + title)
                .imageUrl("/images/test-banner.jpg")
                .linkUrl("/products")
                .buttonText("Xem ngay")
                .sortOrder(sortOrder)
                .isActive(active)
                .startAt(start)
                .endAt(end)
                .build();
        return bannerRepository.save(b);
    }

    @Test
    @DisplayName("Banner CRUD: Thêm, sửa, toggle trạng thái, và xóa banner")
    void testBannerCrudLifecycle() {
        Banner b = createTestBanner("Khuyến Mãi Hè", 2, true, null, null);
        assertThat(b.getId()).isNotNull();

        Banner found = bannerService.get(b.getId());
        assertThat(found.getTitle()).isEqualTo("Khuyến Mãi Hè");
        assertThat(found.getSortOrder()).isEqualTo(2);

        // Toggle status
        boolean activeAfterToggle = bannerService.toggle(b.getId());
        assertThat(activeAfterToggle).isFalse();
        assertThat(bannerService.get(b.getId()).getIsActive()).isFalse();

        // Update
        BannerFormDto updateDto = BannerFormDto.from(bannerService.get(b.getId()));
        updateDto.setTitle("Khuyến Mãi Đông");
        updateDto.setSortOrder(1);
        updateDto.setActive(true);
        Banner updated = bannerService.save(b.getId(), updateDto);
        assertThat(updated.getTitle()).isEqualTo("Khuyến Mãi Đông");
        assertThat(updated.getSortOrder()).isEqualTo(1);
        assertThat(updated.getIsActive()).isTrue();

        // Delete
        bannerService.delete(b.getId());
        assertThrows(IllegalArgumentException.class, () -> bannerService.get(b.getId()));
    }

    @Test
    @DisplayName("Lọc Banner hiển thị theo trạng thái và thời gian (Schedule & Active)")
    void testDisplayableBannersFiltering() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Đang hoạt động, không hẹn giờ -> Hiển thị
        Banner b1 = createTestBanner("Banner 1 Luôn hiện", 1, true, null, null);

        // 2. Bị tắt -> Không hiển thị
        createTestBanner("Banner 2 Tắt", 2, false, null, null);

        // 3. Trong khung giờ hợp lệ -> Hiển thị
        Banner b3 = createTestBanner("Banner 3 Đang trong lịch", 3, true, now.minusDays(1), now.plusDays(2));

        // 4. Lịch ở tương lai -> Không hiển thị
        createTestBanner("Banner 4 Tương lai", 4, true, now.plusDays(1), now.plusDays(3));

        // 5. Đã hết hạn -> Không hiển thị
        createTestBanner("Banner 5 Hết hạn", 5, true, now.minusDays(3), now.minusDays(1));

        List<Banner> displayable = bannerService.displayable();
        List<Integer> ids = displayable.stream().map(Banner::getId).toList();

        assertThat(ids).contains(b1.getId(), b3.getId());
        assertThat(ids).doesNotContain(
                displayable.stream().filter(b -> b.getTitle().contains("Tắt")).map(Banner::getId).findFirst().orElse(-1),
                displayable.stream().filter(b -> b.getTitle().contains("Tương lai")).map(Banner::getId).findFirst().orElse(-1),
                displayable.stream().filter(b -> b.getTitle().contains("Hết hạn")).map(Banner::getId).findFirst().orElse(-1)
        );

        // Thứ tự hiển thị phải đúng theo sortOrder tăng dần
        assertThat(ids.indexOf(b1.getId())).isLessThan(ids.indexOf(b3.getId()));
    }

    @Test
    @DisplayName("Kiểm tra trạng thái hiển thị (DisplayState enum)")
    void testDisplayState() {
        LocalDateTime now = LocalDateTime.now();

        Banner active = createTestBanner("Active", 1, true, null, null);
        assertThat(active.getDisplayState()).isEqualTo(Banner.State.SHOWING);

        Banner hidden = createTestBanner("Hidden", 1, false, null, null);
        assertThat(hidden.getDisplayState()).isEqualTo(Banner.State.HIDDEN);

        Banner scheduled = createTestBanner("Scheduled", 1, true, now.plusDays(1), now.plusDays(5));
        assertThat(scheduled.getDisplayState()).isEqualTo(Banner.State.SCHEDULED);

        Banner expired = createTestBanner("Expired", 1, true, now.minusDays(5), now.minusDays(1));
        assertThat(expired.getDisplayState()).isEqualTo(Banner.State.EXPIRED);
    }

    @Test
    @DisplayName("Validation: Kiểm tra các quy tắc dữ liệu của Banner")
    void testValidationRules() {
        BannerFormDto emptyTitle = new BannerFormDto();
        emptyTitle.setTitle("   ");
        assertThrows(IllegalArgumentException.class, () -> bannerService.save(null, emptyTitle));

        BannerFormDto invalidLink = new BannerFormDto();
        invalidLink.setTitle("Hợp lệ");
        invalidLink.setLinkUrl("javascript:alert(1)");
        assertThrows(IllegalArgumentException.class, () -> bannerService.save(null, invalidLink));

        BannerFormDto spaceLink = new BannerFormDto();
        spaceLink.setTitle("Hợp lệ");
        spaceLink.setLinkUrl("/products with spaces");
        assertThrows(IllegalArgumentException.class, () -> bannerService.save(null, spaceLink));

        BannerFormDto invalidSchedule = new BannerFormDto();
        invalidSchedule.setTitle("Hợp lệ");
        invalidSchedule.setLinkUrl("/products");
        invalidSchedule.setStartAt(LocalDateTime.now().plusDays(5));
        invalidSchedule.setEndAt(LocalDateTime.now().plusDays(2)); // End before start
        assertThrows(IllegalArgumentException.class, () -> bannerService.save(null, invalidSchedule));

        BannerFormDto pastEnd = new BannerFormDto();
        pastEnd.setTitle("Hợp lệ");
        pastEnd.setLinkUrl("/products");
        pastEnd.setEndAt(LocalDateTime.now().minusDays(1)); // End in the past
        assertThrows(IllegalArgumentException.class, () -> bannerService.save(null, pastEnd));

        BannerFormDto invalidSort = new BannerFormDto();
        invalidSort.setTitle("Hợp lệ");
        invalidSort.setLinkUrl("/products");
        invalidSort.setSortOrder(-1);
        assertThrows(IllegalArgumentException.class, () -> bannerService.save(null, invalidSort));
    }

    @Test
    @DisplayName("Thứ tự hiển thị: Chọn vị trí (Dẫn đầu, vị trí hiện có, cuối cùng) và tự động chuẩn hóa số thứ tự")
    void testPositionResequencing() {
        Banner b1 = createTestBanner("Banner 1", 1, true, null, null);
        Banner b2 = createTestBanner("Banner 2", 2, true, null, null);

        // Giả lập chọn "Dẫn đầu" (sortOrder = 0) cho banner mới
        Banner b3 = createTestBanner("Banner 3 Mới", 0, true, null, null);
        BannerFormDto dto3 = BannerFormDto.from(b3);
        dto3.setSortOrder(0);
        bannerService.save(b3.getId(), dto3);

        Banner found3 = bannerService.get(b3.getId());
        Banner found1 = bannerService.get(b1.getId());
        Banner found2 = bannerService.get(b2.getId());

        assertThat(found3.getSortOrder()).isEqualTo(1);
        assertThat(found1.getSortOrder()).isEqualTo(2);
        assertThat(found2.getSortOrder()).isEqualTo(3);

        // Nút bấm mặc định luôn là "Khám phá"
        assertThat(found3.getButtonText()).isEqualTo("Khám phá");

        // Xóa banner 1 (xóa mềm) -> các banner còn lại được chuẩn hóa thứ tự liên tục 1, 2
        bannerService.delete(b1.getId());
        assertThat(bannerService.get(b3.getId()).getSortOrder()).isEqualTo(1);
        assertThat(bannerService.get(b2.getId()).getSortOrder()).isEqualTo(2);

        // Kiểm tra xóa mềm: bản ghi vẫn còn trong database nhưng có cờ isDeleted = true, deletedAt != null
        Banner rawB1 = bannerRepository.findById(b1.getId()).orElseThrow();
        assertThat(rawB1.getIsDeleted()).isTrue();
        assertThat(rawB1.getDeletedAt()).isNotNull();
        assertThat(bannerService.listAll()).extracting(Banner::getId).doesNotContain(b1.getId());
        assertThrows(IllegalArgumentException.class, () -> bannerService.get(b1.getId()));
    }
}
