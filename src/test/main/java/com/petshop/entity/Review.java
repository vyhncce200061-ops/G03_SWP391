package com.petshop.entity;

import com.petshop.entity.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Customer product rating and review attached to verified purchased OrderItem.
 * Mapped to table dbo.Reviews.
 */
@Entity
@Table(name = "Reviews", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_Reviews_OrderItem", columnNames = {"OrderItemId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OrderItemId", nullable = false, unique = true)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerId", nullable = false)
    private User customer;

    @Column(name = "Rating", nullable = false)
    private Integer rating;

    @Column(name = "Comment", length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.VISIBLE;

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
            this.status = ReviewStatus.VISIBLE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
