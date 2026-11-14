package com.petshop;

import com.petshop.config.security.CustomUserDetails;
import com.petshop.controller.customer.CustomerReviewController;
import com.petshop.dto.ReviewCreateDto;
import com.petshop.entity.User;
import com.petshop.service.ReviewService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CustomerReviewRedirectTests {
    @ParameterizedTest
    @CsvSource({
        "/customer/orders/42,,/customer/orders/42",
        "/customer/profile?tab=orders,,/customer/profile?tab=orders",
        ",http://localhost:8081/customer/orders/42,http://localhost:8081/customer/orders/42",
        "https://example.invalid/steal,,/customer/reviews",
        "//example.invalid/steal,,/customer/reviews",
        "/%2fexample.invalid,,/customer/reviews",
        "/%5cexample.invalid,,/customer/reviews",
        ",https://example.invalid/customer/profile,/customer/reviews",
        ",http://localhost:8081/customer/profile?tab=orders,/customer/profile?tab=orders",
        "/admin/users,,/customer/reviews"
    })
    void returnsOnlyToInternalCustomerPages(String redirect, String referer, String expected) {
        var request=new MockHttpServletRequest();request.setScheme("http");request.setServerName("localhost");request.setServerPort(8081);
        var form=new ReviewCreateDto();form.setRating(5);form.setComment("Good");
        var controller=new CustomerReviewController(mock(ReviewService.class));
        String result=controller.create(new CustomUserDetails(User.builder().id(1L).build()),1L,redirect,form,
                new BeanPropertyBindingResult(form,"reviewForm"),new ExtendedModelMap(),new RedirectAttributesModelMap(),referer,request);
        // Same-origin absolute Referer is reduced to its local path.
        if(expected.startsWith("http://localhost:8081"))expected=expected.substring("http://localhost:8081".length());
        assertEquals("redirect:"+expected,result);
    }
}
