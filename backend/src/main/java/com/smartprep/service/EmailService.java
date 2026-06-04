package com.smartprep.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (password reset, email verification) via SMTP.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@smartprep.com}")
    private String fromAddress;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Send a password reset email with a reset link.
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        String subject = "IELTS SmartPrep — Reset Your Password";
        String html = buildResetEmailHtml(resetUrl);

        sendHtmlEmail(toEmail, subject, html);
    }

    /**
     * Send an email verification link.
     */
    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = frontendUrl + "/verify-email?token=" + token;
        String subject = "IELTS SmartPrep — Verify Your Email";
        String html = buildVerificationEmailHtml(verifyUrl);

        sendHtmlEmail(toEmail, subject, html);
    }

    private void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to: {} subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {} — {}", to, e.getMessage(), e);
        }
    }

    private String buildResetEmailHtml(String resetUrl) {
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 32px; background: #f8f9fa; border-radius: 12px;">
              <div style="text-align: center; margin-bottom: 24px;">
                <h1 style="color: #6c5ce7; margin: 0; font-size: 24px;">IELTS SmartPrep</h1>
              </div>
              <div style="background: white; padding: 32px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);">
                <h2 style="margin-top: 0; color: #2d3436;">Reset Your Password</h2>
                <p style="color: #636e72; line-height: 1.6;">
                  We received a request to reset your password. Click the button below to create a new password.
                  This link will expire in <strong>15 minutes</strong>.
                </p>
                <div style="text-align: center; margin: 28px 0;">
                  <a href="%s" style="display: inline-block; padding: 14px 36px; background: linear-gradient(135deg, #6c5ce7, #a29bfe); color: white; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px;">Reset Password</a>
                </div>
                <p style="color: #b2bec3; font-size: 13px; margin-bottom: 0;">
                  If you didn't request this, you can safely ignore this email. Your password won't change.
                </p>
              </div>
              <p style="text-align: center; color: #b2bec3; font-size: 12px; margin-top: 16px;">
                © IELTS SmartPrep — AI-Powered IELTS Practice
              </p>
            </div>
            """.formatted(resetUrl);
    }

    private String buildVerificationEmailHtml(String verifyUrl) {
        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 32px; background: #f8f9fa; border-radius: 12px;">
              <div style="text-align: center; margin-bottom: 24px;">
                <h1 style="color: #6c5ce7; margin: 0; font-size: 24px;">IELTS SmartPrep</h1>
              </div>
              <div style="background: white; padding: 32px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);">
                <h2 style="margin-top: 0; color: #2d3436;">Verify Your Email</h2>
                <p style="color: #636e72; line-height: 1.6;">
                  Welcome to IELTS SmartPrep! Please verify your email address by clicking the button below.
                  This link will expire in <strong>24 hours</strong>.
                </p>
                <div style="text-align: center; margin: 28px 0;">
                  <a href="%s" style="display: inline-block; padding: 14px 36px; background: linear-gradient(135deg, #00b894, #55efc4); color: white; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px;">Verify Email</a>
                </div>
                <p style="color: #b2bec3; font-size: 13px; margin-bottom: 0;">
                  If you didn't create an account, you can safely ignore this email.
                </p>
              </div>
              <p style="text-align: center; color: #b2bec3; font-size: 12px; margin-top: 16px;">
                © IELTS SmartPrep — AI-Powered IELTS Practice
              </p>
            </div>
            """.formatted(verifyUrl);
    }
}
