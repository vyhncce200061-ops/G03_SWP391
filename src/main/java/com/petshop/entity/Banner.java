package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Homepage banner / slider. Mapped to dbo.Banners. */
@Entity
@Table(name = "Banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    public enum State { SHOWING, HIDDEN, SCHEDULED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Integer id;

    @Column(name = "Title", nullable = false, length = 150)
    private String title;

    @Column(name = "Subtitle", length = 300)
    private String subtitle;

    @Column(name = "ImageUrl", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "LinkUrl", length = 500)
    private String linkUrl;

    @Column(name = "ButtonText", length = 50)
    private String buttonText;

    @Column(name = "SortOrder", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "IsActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "StartAt")
    private LocalDateTime startAt;

    @Column(name = "EndAt")
    private LocalDateTime endAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    /** True when the banner is switched on and inside its optional schedule window. */
    @Transient
    public boolean isDisplayableAt(LocalDateTime now) {
        return Boolean.TRUE.equals(isActive)
                && (startAt == null || !startAt.isAfter(now))
                && (endAt == null || endAt.isAfter(now));
    }

    @Transient
    public State getDisplayState() {
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(isActive)) return State.HIDDEN;
        if (startAt != null && startAt.isAfter(now)) return State.SCHEDULED;
        if (endAt != null && !endAt.isAfter(now)) return State.EXPIRED;
        return State.SHOWING;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (isActive == null) isActive = true;
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
