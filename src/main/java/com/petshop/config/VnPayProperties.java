package com.petshop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Merchant credentials are supplied by the deployment; never log this bean. */
@Component
@ConfigurationProperties(prefix = "vnpay")
@Getter
@Setter
public class VnPayProperties {
    private String tmnCode = "";
    private String hashSecret = "";
    private String returnUrl = "";
}
