package com.petshop.config;

import java.io.IOException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Opens the local development site once per JVM, including across DevTools restarts. */
@Component
@Profile("dev & !test")
@ConditionalOnProperty(name = "petshop.browser.auto-open", havingValue = "true")
public class DevBrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(DevBrowserLauncher.class);
    private static final String OPENED_PROPERTY = "petshop.browser.opened";

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof ServletWebServerApplicationContext context)) {
            return;
        }

        String url = (sslEnabled ? "https" : "http") + "://localhost:"
                + context.getWebServer().getPort() + context.getServletContext().getContextPath() + "/";
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command;
        if (os.startsWith("windows")) {
            // Spring Boot runs headless by default, so Desktop.browse is unavailable.
            command = new String[] { "rundll32.exe", "url.dll,FileProtocolHandler", url };
        } else if (os.startsWith("mac")) {
            command = new String[] { "open", url };
        } else if (System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null) {
            command = new String[] { "xdg-open", url };
        } else {
            log.info("Open PetShop at {}", url);
            return;
        }

        // System properties are shared by the original and restarted DevTools classloaders.
        synchronized (System.getProperties()) {
            if (Boolean.getBoolean(OPENED_PROPERTY)) {
                return;
            }
            try {
                new ProcessBuilder(command).start();
                System.setProperty(OPENED_PROPERTY, "true");
                log.info("Opening PetShop in the default browser: {}", url);
            } catch (IOException | SecurityException ex) {
                log.warn("Could not open the browser automatically. Open {} manually: {}", url, ex.getMessage());
            }
        }
    }
}
