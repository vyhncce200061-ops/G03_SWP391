package com.petshop.config;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Web MVC Configuration.
 * Registers Thymeleaf Layout Dialect and maps static asset locations.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Registers the LayoutDialect bean required for Thymeleaf hierarchical decorators:
     * xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
     * layout:decorate="~{layout/main-layout}"
     */
    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }

    /**
     * Configures custom resource handlers for static CSS, JS, Images, and Webjars.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**", "/css/**", "/js/**", "/images/**", "/webjars/**")
                .addResourceLocations(
                        "classpath:/static/",
                        "classpath:/static/css/",
                        "classpath:/static/js/",
                        "classpath:/static/images/",
                        "classpath:/META-INF/resources/webjars/"
                )
                .setCachePeriod(0); // Cache disabled during development for instant updates
    }

    /**
     * Static view controllers for simple pages without complex controller logic.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error/403").setViewName("error/403");
        registry.addViewController("/error/404").setViewName("error/404");
        registry.addViewController("/error/500").setViewName("error/500");
    }
}
