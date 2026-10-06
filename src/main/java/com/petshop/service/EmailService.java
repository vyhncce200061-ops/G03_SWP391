package com.petshop.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends account-related emails. If no SMTP account is configured,
 * the link is logged instead so development still works.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail-from:}")
    private String from;

    public void sendRegistrationConfirmation(String to, String fullName, String link) {
        if (from == null || from.isBlank()) {
            log.warn("MAIL_USERNAME not configured. Verification link for {}: {}", to, link);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("PetShop - Xác nhận đăng ký tài khoản");
            helper.setText("<p>Xin chào <b>" + escape(fullName) + "</b>,</p>"
                    + "<p>Cảm ơn bạn đã đăng ký PetShop. Vui lòng bấm vào liên kết dưới đây để kích hoạt tài khoản "
                    + "(có hiệu lực trong 24 giờ):</p>"
                    + "<p><a href=\"" + link + "\">Xác nhận tài khoản</a></p>"
                    + "<p>Nếu bạn không thực hiện đăng ký này, hãy bỏ qua email.</p>", true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send confirmation email to {}", to, e);
            throw new IllegalStateException("Không thể gửi email xác nhận. Vui lòng thử lại sau.");
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
