package com.petshop;

import com.petshop.controller.admin.CategoryManageController;
import com.petshop.service.CategoryManageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CategoryActionConflictTests {
    @Test void aReferenceAddedDuringDeletionReturnsAnErrorInsteadOfSuccessOrAnErrorPage() {
        var service = mock(CategoryManageService.class);
        when(service.delete(10)).thenThrow(new DataIntegrityViolationException("concurrent foreign key reference"));
        var redirect = new RedirectAttributesModelMap();
        assertThat(new CategoryManageController(service).deleteCategory(10, redirect))
                .isEqualTo("redirect:/admin/categories");
        assertThat(redirect.getFlashAttributes()).containsKey("errorMessage").doesNotContainKey("successMessage");
        assertThat(redirect.getFlashAttributes().get("errorMessage").toString()).contains("không thể xóa", "ngừng hoạt động");
    }

    @ParameterizedTest @ValueSource(strings = {"delete", "activate", "deactivate"})
    void concurrentLockConflictsReturnARetryMessageWithoutClaimingSuccess(String action) {
        var service = mock(CategoryManageService.class);
        when(service.delete(10)).thenThrow(new PessimisticLockingFailureException("concurrent update"));
        when(service.setActive(eq(10), anyBoolean())).thenThrow(new PessimisticLockingFailureException("concurrent update"));
        var controller = new CategoryManageController(service);
        var redirect = new RedirectAttributesModelMap();
        String target = switch (action) {
            case "delete" -> controller.deleteCategory(10, redirect);
            case "activate" -> controller.activateCategory(10, redirect);
            default -> controller.deactivateCategory(10, redirect);
        };
        assertThat(target).isEqualTo("redirect:/admin/categories");
        assertThat(redirect.getFlashAttributes()).containsKey("errorMessage").doesNotContainKey("successMessage");
        assertThat(redirect.getFlashAttributes().get("errorMessage").toString()).contains("tải lại danh sách");
    }
}
