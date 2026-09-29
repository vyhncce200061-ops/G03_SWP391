package com.petshop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User account entity (Customer, Staff, Admin).
 * Mapped to table dbo.Users.
 */
@Entity
@Table(name = "Users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "RoleId", nullable = false)
    private Role role;

    @Column(name = "Email", length = 255)
    private String email;

    @Column(name = "Phone", length = 20)
    private String phone;

    @Column(name = "PasswordHash", nullable = false, length = 500)
    private String passwordHash;

    @Column(name = "FullName", nullable = false, length = 150)
    private String fullName;

    @Column(name = "AvatarUrl", length = 1000)
    private String avatarUrl;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "LastLoginAt")
    private LocalDateTime lastLoginAt;

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
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
