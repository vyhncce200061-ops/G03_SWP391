package com.petshop.dto.banner;

import com.petshop.entity.Banner;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class BannerFormDto {
    @NotBlank(message = "Vui lòng nhập tiêu đề banner.")
    @Size(max = 150, message = "Tiêu đề tối đa 150 ký tự.")
    private String title;

    @Size(max = 300, message = "Mô tả ngắn tối đa 300 ký tự.")
    private String subtitle;

    @Size(max = 500, message = "Liên kết tối đa 500 ký tự.")
    private String linkUrl;

    @Size(max = 50, message = "Chữ trên nút tối đa 50 ký tự.")
    private String buttonText = "Khám phá";

    @NotNull(message = "Vui lòng chọn vị trí hiển thị.")
    @Min(value = 0, message = "Thứ tự hiển thị từ 0 đến 9999.")
    @Max(value = 9999, message = "Thứ tự hiển thị từ 0 đến 9999.")
    private Integer sortOrder = 9999;

    private boolean active = true;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    private MultipartFile imageFile;

    public static BannerFormDto from(Banner b) {
        BannerFormDto dto = new BannerFormDto();
        dto.title = b.getTitle();
        dto.subtitle = b.getSubtitle();
        dto.linkUrl = b.getLinkUrl();
        dto.buttonText = b.getButtonText();
        dto.sortOrder = b.getSortOrder();
        dto.active = Boolean.TRUE.equals(b.getIsActive());
        dto.startAt = b.getStartAt();
        dto.endAt = b.getEndAt();
        return dto;
    }
}
