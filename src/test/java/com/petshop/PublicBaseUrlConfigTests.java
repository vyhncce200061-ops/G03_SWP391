package com.petshop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicBaseUrlConfigTests {
    private MockEnvironment configuration() throws Exception {
        var environment=new MockEnvironment();
        for(var source:new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }

    @ParameterizedTest
    @ValueSource(strings={"8081","8093"})
    void defaultEmailLinksFollowTheApplicationPort(String port) throws Exception {
        var environment=configuration().withProperty("server.port",port);
        assertEquals("http://localhost:"+port,environment.getProperty("petshop.public-base-url"));
    }

    @Test void defaultConfigurationUsesItsConfiguredServerPort() throws Exception {
        var environment=configuration();
        assertEquals("http://localhost:"+environment.getProperty("server.port"),environment.getProperty("petshop.public-base-url"));
    }

    @Test void anExplicitPublicUrlTakesPrecedenceOverLegacyUrlAndPort() throws Exception {
        var environment=configuration().withProperty("server.port","8093")
                .withProperty("APP_BASE_URL","https://legacy.example.test")
                .withProperty("PUBLIC_BASE_URL","https://demo.example.test");
        assertEquals("https://demo.example.test",environment.getProperty("petshop.public-base-url"));
    }

    @Test void legacyAppBaseUrlStillWorks() throws Exception {
        var environment=configuration().withProperty("APP_BASE_URL","https://legacy.example.test");
        assertEquals("https://legacy.example.test",environment.getProperty("petshop.public-base-url"));
    }
}
