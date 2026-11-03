package com.petshop.service.impl;

import com.petshop.entity.Review;
import com.petshop.entity.enums.ReviewStatus;
import com.petshop.repository.ReviewRepository;
import com.petshop.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final com.petshop.repository.OrderItemRepository items;
    private final com.petshop.repository.UserRepository users;
    private final jakarta.validation.Validator validator;
    private final jakarta.persistence.EntityManager em;

    @Override @Transactional(readOnly=true)
    public com.petshop.entity.OrderItem eligibleItem(Long customerId, Long itemId) {
        var item = items.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm đã mua."));
        checkPurchase(item, customerId);
        if (reviewRepository.existsByOrderItemId(itemId)) throw new IllegalArgumentException("Bạn đã đánh giá sản phẩm trong đơn này. Xem tại Đánh giá của tôi.");
        return item;
    }

    private void checkPurchase(com.petshop.entity.OrderItem item, Long customerId) {
        var order = item.getOrder();
        if (!order.getCustomer().getId().equals(customerId)) throw new IllegalArgumentException("Không tìm thấy sản phẩm đã mua.");
        if (order.getOrderStatus() != com.petshop.entity.enums.OrderStatus.COMPLETED
                || order.getPaymentStatus() != com.petshop.entity.enums.PaymentStatus.PAID)
            throw new IllegalArgumentException("Chỉ đánh giá sản phẩm thuộc đơn đã hoàn tất và thanh toán.");
    }

    @Override @Transactional
    public Review createReview(Long customerId, Long itemId, com.petshop.dto.ReviewCreateDto form) {
        if (form != null && form.getComment() != null) form.setComment(form.getComment().trim());
        if (form == null || !validator.validate(form).isEmpty()) throw new IllegalArgumentException("Chọn 1–5 sao; nhận xét từ 10 đến 1000 ký tự.");
        var customer = users.findByIdForUpdate(customerId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        em.refresh(customer, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(customer.getStatus()) || !"CUSTOMER".equals(customer.getRole().getCode())) throw new IllegalArgumentException("Tài khoản không được gửi đánh giá.");
        var item = items.findByIdForUpdate(itemId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm đã mua."));
        checkPurchase(item, customerId);
        if (reviewRepository.existsByOrderItemId(itemId)) throw new IllegalArgumentException("Bạn đã đánh giá sản phẩm trong đơn này.");
        return reviewRepository.saveAndFlush(Review.builder().customer(customer).orderItem(item).rating(form.getRating())
            .comment(form.getComment()==null || form.getComment().isBlank() ? null : form.getComment().trim()).build());
    }

    @Override @Transactional(readOnly=true)
    public Page<Review> myReviews(Long customerId, Pageable pageable) {
        return reviewRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable);
    }

    @Override @Transactional(readOnly=true)
    public Page<Review> publicReviews(Long productId, Pageable pageable) {
        return reviewRepository.findVisibleReviewsByProductId(productId, pageable);
    }


    @Override
    @Transactional
    public Review reply(Long reviewId, Long adminId, String text) {
        if (text == null || text.isBlank() || text.length() > 1000) {
            throw new IllegalArgumentException("Phản hồi cần có nội dung, tối đa 1000 ký tự.");
        }
        var admin = users.findByIdForUpdate(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        em.refresh(admin, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!"ACTIVE".equals(admin.getStatus()) || !"ADMIN".equals(admin.getRole().getCode())) {
            throw new IllegalArgumentException("Chỉ Admin đang hoạt động được trả lời đánh giá.");
        }
        // Serialize replies and moderation on the same review. A stale submit must
        // not overwrite a reply already sent by another admin or undo visibility.
        var review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá."));
        em.refresh(review, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (review.getReplyText() != null) {
            throw new IllegalArgumentException("Đánh giá đã được trả lời. Vui lòng tải lại trang.");
        }
        review.setReplyText(text.strip());
        review.setRepliedBy(admin);
        review.setRepliedAt(java.time.LocalDateTime.now());
        return reviewRepository.saveAndFlush(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Review> searchReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable) {
        String safeKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return reviewRepository.searchReviewsAdmin(safeKeyword, rating, status, pageable);
    }

    @Override
    @Transactional
    public Review toggleReviewStatus(Long reviewId) {
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá với mã #" + reviewId));

        em.refresh(review, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (review.getStatus() == ReviewStatus.VISIBLE) {
            review.setStatus(ReviewStatus.HIDDEN);
            log.info("Review #{} hidden by admin", reviewId);
        } else {
            review.setStatus(ReviewStatus.VISIBLE);
            log.info("Review #{} visible by admin", reviewId);
        }

        return reviewRepository.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Review getReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá với mã #" + reviewId));
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalReviewsCount() {
        return reviewRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long getVisibleReviewsCount() {
        return reviewRepository.countByStatus(ReviewStatus.VISIBLE);
    }

    @Override
    @Transactional(readOnly = true)
    public long getHiddenReviewsCount() {
        return reviewRepository.countByStatus(ReviewStatus.HIDDEN);
    }

    @Override
    @Transactional(readOnly = true)
    public Double getAverageRating() {
        return reviewRepository.getOverallAverageRating();
    }
}
