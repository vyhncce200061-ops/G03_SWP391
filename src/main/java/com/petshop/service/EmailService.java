package com.petshop.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * HTML email transport adapted from Bao's ft/auth commit 4233a19.
 * Missing configuration and SMTP failures are reported to the caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenders;

    @Value("${petshop.mail-from:${app.mail-from:}}")
    private String from;

    @Value("${spring.mail.host:}")
    private String host;

    public void requireConfigured() {
        if (host == null || host.isBlank() || from == null || from.isBlank() || mailSenders.getIfAvailable() == null) {
            throw new IllegalStateException("Chưa cấu hình SMTP/email gửi. Vui lòng cấu hình mail trước khi cấp tài khoản hoặc gửi liên kết.");
        }
    }

    public void sendRegistrationConfirmation(String to, String fullName, String link) {
        requireConfigured();
        try {
            JavaMailSender mailSender = mailSenders.getObject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("PetShop - Xác nhận đăng ký tài khoản");
            helper.setText(buildHtml(escape(fullName), org.springframework.web.util.HtmlUtils.htmlEscape(link)), true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send confirmation email to {}", to, e);
            throw new IllegalStateException("Không thể gửi email xác nhận. Vui lòng thử lại sau.");
        }
    }

    public void sendPasswordResetEmail(String to, String fullName, String link) {
        requireConfigured();
        try {
            JavaMailSender mailSender = mailSenders.getObject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("PetShop - Đặt lại mật khẩu tài khoản");
            helper.setText(buildResetPasswordHtml(escape(fullName), org.springframework.web.util.HtmlUtils.htmlEscape(link)), true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", to, e);
            throw new IllegalStateException("Không thể gửi email đặt lại mật khẩu. Vui lòng thử lại sau.");
        }
    }

    public void sendStaffInvitation(String to, String fullName, String link) {
        requireConfigured();
        try {
            JavaMailSender mailSender = mailSenders.getObject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("PetShop - Mời nhận tài khoản nhân viên");
            String safeLink = org.springframework.web.util.HtmlUtils.htmlEscape(link);
            helper.setText("<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;line-height:1.6\">"
                    + "<h2>Nhận tài khoản nhân viên PetShop</h2><p>Xin chào " + escape(fullName) + ",</p>"
                    + "<p>Quản trị viên đã cấp cho bạn tài khoản STAFF với email <b>" + escape(to) + "</b>.</p>"
                    + "<p>Hãy tự đặt mật khẩu bằng liên kết dùng một lần sau. Liên kết có hiệu lực 30 phút.</p>"
                    + "<p><a href=\"" + safeLink + "\">Đặt mật khẩu và nhận tài khoản</a></p>"
                    + "<p>Sau khi đặt mật khẩu, đăng nhập bằng email này và mật khẩu bạn vừa chọn.</p>"
                    + "<p>Nếu liên kết hết hạn hoặc chưa nhận được thư, liên hệ Admin để gửi lại lời mời.</p></div>", true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể gửi email nhận tài khoản. Vui lòng kiểm tra SMTP và thử lại.", e);
        }
    }

    /** Table-based layout with inline styles so it renders consistently across email clients. */
    private String buildHtml(String fullName, String link) {
        return "<style>"
                + ".btn-confirm{transition:background .2s ease,box-shadow .2s ease;}"
                + ".btn-confirm:hover{background:#14b8a6 !important;border-color:#0d9488 !important;"
                + "box-shadow:0 6px 16px rgba(13,148,136,.45);}"
                + "</style>"
                + "<div style=\"background:#f3f4f6;padding:32px 12px;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" align=\"center\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;"
                + "border:1px solid #e5e7eb;overflow:hidden;\">"
                // header
                + "<tr><td align=\"center\" style=\"background:#0d9488;padding:28px 24px;\">"
                + "<div style=\"font-size:28px;font-weight:bold;color:#ffffff;letter-spacing:1px;\">\uD83D\uDC3E PetShop</div>"
                + "</td></tr>"
                // body
                + "<tr><td style=\"padding:36px 32px 12px 32px;color:#374151;font-size:16px;line-height:1.6;\">"
                + "<div style=\"font-size:22px;font-weight:bold;color:#111827;margin-bottom:12px;\">"
                + "Xin chào " + fullName + ",</div>"
                + "Cảm ơn bạn đã đăng ký tài khoản tại PetShop. "
                + "Chỉ còn một bước nữa thôi, hãy bấm nút bên dưới để kích hoạt tài khoản của bạn."
                + "</td></tr>"
                // button
                + "<tr><td align=\"center\" style=\"padding:20px 32px 28px 32px;\">"
                + "<a href=\"" + link + "\" class=\"btn-confirm\" target=\"_blank\" style=\"display:inline-block;background:#0d9488;"
                + "color:#ffffff;text-decoration:none;font-size:20px;font-weight:bold;"
                + "padding:18px 48px;border-radius:14px;border:3px solid #0f766e;\">"
                + "Xác nhận tài khoản</a>"
                + "</td></tr>"
                // footer
                + "<tr><td style=\"padding:0 32px 32px 32px;color:#6b7280;font-size:13px;line-height:1.6;\">"
                + "Liên kết có hiệu lực trong <b>24 giờ</b>. Nếu nút không bấm được, hãy sao chép đường dẫn sau "
                + "vào trình duyệt:<br><a href=\"" + link + "\" style=\"color:#0d9488;word-break:break-all;\">"
                + link + "</a>"
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:20px 0;\">"
                + "Nếu bạn không thực hiện đăng ký này, hãy bỏ qua email."
                + "</td></tr>"
                + "</table></div>";
    }

    private String buildResetPasswordHtml(String fullName, String link) {
        return "<style>"
                + ".btn-confirm{transition:background .2s ease,box-shadow .2s ease;}"
                + ".btn-confirm:hover{background:#14b8a6 !important;border-color:#0d9488 !important;"
                + "box-shadow:0 6px 16px rgba(13,148,136,.45);}"
                + "</style>"
                + "<div style=\"background:#f3f4f6;padding:32px 12px;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" align=\"center\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;"
                + "border:1px solid #e5e7eb;overflow:hidden;\">"
                // header
                + "<tr><td align=\"center\" style=\"background:#0d9488;padding:28px 24px;\">"
                + "<div style=\"font-size:28px;font-weight:bold;color:#ffffff;letter-spacing:1px;\">\uD83D\uDC3E PetShop</div>"
                + "</td></tr>"
                // body
                + "<tr><td style=\"padding:36px 32px 12px 32px;color:#374151;font-size:16px;line-height:1.6;\">"
                + "<div style=\"font-size:22px;font-weight:bold;color:#111827;margin-bottom:12px;\">"
                + "Xin chào " + fullName + ",</div>"
                + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản PetShop của bạn. "
                + "Vui lòng bấm vào nút bên dưới để tiến hành đổi mật khẩu mới (liên kết có hiệu lực trong 30 phút)."
                + "</td></tr>"
                // button
                + "<tr><td align=\"center\" style=\"padding:20px 32px 28px 32px;\">"
                + "<a href=\"" + link + "\" class=\"btn-confirm\" target=\"_blank\" style=\"display:inline-block;background:#0d9488;"
                + "color:#ffffff;text-decoration:none;font-size:20px;font-weight:bold;"
                + "padding:18px 48px;border-radius:14px;border:3px solid #0f766e;\">"
                + "Đặt lại mật khẩu</a>"
                + "</td></tr>"
                // footer
                + "<tr><td style=\"padding:0 32px 32px 32px;color:#6b7280;font-size:13px;line-height:1.6;\">"
                + "Liên kết có hiệu lực trong <b>30 phút</b>. Nếu nút không bấm được, hãy sao chép đường dẫn sau "
                + "vào trình duyệt:<br><a href=\"" + link + "\" style=\"color:#0d9488;word-break:break-all;\">"
                + link + "</a>"
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:20px 0;\">"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, xin hãy bỏ qua email này. Tài khoản của bạn vẫn được bảo mật an toàn."
                + "</td></tr>"
                + "</table></div>";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
