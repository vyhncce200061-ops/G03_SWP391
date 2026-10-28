package com.petshop.service;

import com.petshop.entity.Review;
import com.petshop.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    com.petshop.entity.OrderItem eligibleItem(Long customerId, Long itemId);
    Review createReview(Long customerId, Long itemId, com.petshop.dto.ReviewCreateDto form);
    Page<Review> myReviews(Long customerId, Pageable pageable);
    Page<Review> publicReviews(Long productId, Pageable pageable);
    Page<Review> searchReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable);
    Review reply(Long reviewId, Long adminId, String text);
    Review toggleReviewStatus(Long reviewId);
    Review getReviewById(Long reviewId);
    long getTotalReviewsCount();
    long getVisibleReviewsCount();
    long getHiddenReviewsCount();
    Double getAverageRating();
}
