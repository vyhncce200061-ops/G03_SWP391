package com.petshop;

import com.petshop.controller.guest.AuthController;
import com.petshop.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationConflictTests {
    @Test
    void simultaneousActivationReturnsFriendlyErrorWithoutDatabaseDetails() {
        AuthService auth = mock(AuthService.class);
        when(auth.confirmRegistration("valid-token")).thenThrow(new DataIntegrityViolationException("unique email SQL details"));
        var flash = new RedirectAttributesModelMap();
        assertEquals("redirect:/login", new AuthController(auth).verifyEmail("valid-token", flash));
        assertTrue(flash.getFlashAttributes().containsKey("errorMessage"));
        assertFalse(flash.getFlashAttributes().toString().contains("SQL details"));
        assertFalse(flash.getFlashAttributes().containsKey("successMessage"));
    }
}
