package com.petshop;

import com.petshop.controller.admin.ReviewManageController;
import com.petshop.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewModerationErrorTests {
    @Test void databaseFailureDoesNotExposeDatabaseDetailsToTheBrowser() {
        var reviews=mock(ReviewService.class);
        when(reviews.toggleReviewStatus(1L)).thenThrow(new DataAccessResourceFailureException("SQL connection details must stay in server logs"));
        var flash=new RedirectAttributesModelMap();
        assertEquals("redirect:/admin/reviews",new ReviewManageController(reviews).toggleStatus(1L,null,null,null,0,20,flash));
        String message=flash.getFlashAttributes().get("errorMessage").toString();
        assertFalse(message.contains("SQL connection"));assertFalse(message.isBlank());
        assertFalse(flash.getFlashAttributes().containsKey("successMessage"));
    }
}
