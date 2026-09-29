package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Customer shipping address entity.
 * Mapped to table dbo.UserAddresses.
 */
@Entity
@Table(name = "UserAddresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerId", nullable = false)
    private User customer;

    @Column(name = "RecipientName", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "RecipientPhone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "Province", nullable = false, length = 100)
    private String province;

    @Column(name = "District", nullable = false, length = 100)
    private String district;

    @Column(name = "Ward", nullable = false, length = 100)
    private String ward;

    @Column(name = "AddressLine", nullable = false, length = 300)
    private String addressLine;

    @Column(name = "IsDefault", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "IsDeleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.isDefault == null) {
            this.isDefault = false;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
