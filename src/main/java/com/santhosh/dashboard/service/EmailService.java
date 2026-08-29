package com.santhosh.dashboard.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger =
            LoggerFactory.getLogger(EmailService.class);

    private static final int MAX_ATTACHMENT_BYTES =
            10 * 1024 * 1024;

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.from:${spring.mail.username:}}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Checks whether the minimum SMTP configuration is available.
     */
    public boolean isConfigured() {
        return hasText(smtpHost)
                && smtpPort > 0
                && hasText(smtpUsername)
                && hasText(smtpPassword);
    }

    /**
     * Returns a safe SMTP configuration summary.
     * Password is never exposed.
     */
    public String getConfigurationSummary() {

        return String.format(
                "SMTP %s:%d (%s)",
                smtpHost,
                smtpPort,
                hasText(smtpUsername)
                        ? maskEmail(smtpUsername)
                        : "credentials missing"
        );
    }

    /**
     * Sends a plain-text email.
     */
    public void sendPlainEmail(
            String to,
            String subject,
            String body) {

        validateConfiguration();
        validateAddress(to);
        requireText(subject, "Subject cannot be empty");

        try {

            SimpleMailMessage message =
                    new SimpleMailMessage();

            message.setFrom(resolveFrom());
            message.setTo(to.trim());
            message.setSubject(subject.trim());
            message.setText(body == null ? "" : body);

            mailSender.send(message);

            logger.info(
                    "Email delivered successfully to {} via {}:{}",
                    maskEmail(to),
                    smtpHost,
                    smtpPort
            );

        } catch (MailAuthenticationException e) {

            logger.error(
                    "SMTP authentication failed for {}",
                    maskEmail(smtpUsername),
                    e
            );

            throw smtpFailure(
                    "SMTP authentication failed. " +
                    "For Gmail, use a 16-character App Password.",
                    e
            );

        } catch (MailSendException e) {

            logger.error(
                    "SMTP server rejected email delivery to {}",
                    maskEmail(to),
                    e
            );

            throw smtpFailure(
                    "SMTP accepted the connection but rejected delivery. " +
                    "Check the sender address and recipient.",
                    e
            );

        } catch (MailException e) {

            logger.error(
                    "SMTP email delivery failed to {}",
                    maskEmail(to),
                    e
            );

            throw smtpFailure(
                    "Email delivery failed: " +
                    safeMessage(e),
                    e
            );

        } catch (Exception e) {

            logger.error(
                    "Unexpected email error while sending to {}",
                    maskEmail(to),
                    e
            );

            throw smtpFailure(
                    "Unexpected email delivery error: " +
                    safeMessage(e),
                    e
            );
        }
    }

    /**
     * Sends an HTML email with an optional attachment.
     */
    public void sendHtmlEmailWithAttachment(
            String to,
            String subject,
            String htmlContent,
            String attachmentName,
            byte[] attachmentData) {

        validateConfiguration();
        validateAddress(to);
        requireText(subject, "Subject cannot be empty");

        if (attachmentData != null
                && attachmentData.length > MAX_ATTACHMENT_BYTES) {

            throw new IllegalArgumentException(
                    "Attachment exceeds the 10 MB limit."
            );
        }

        try {

            MimeMessage message =
                    mailSender.createMimeMessage();

            boolean hasAttachment =
                    attachmentData != null
                            && attachmentData.length > 0;

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            hasAttachment,
                            StandardCharsets.UTF_8.name()
                    );

            helper.setFrom(resolveFrom());
            helper.setTo(to.trim());
            helper.setSubject(subject.trim());

            helper.setText(
                    htmlContent == null ? "" : htmlContent,
                    true
            );

            if (hasAttachment) {

                String safeName =
                        sanitizeAttachmentName(attachmentName);

                helper.addAttachment(
                        safeName,
                        new ByteArrayResource(attachmentData)
                );
            }

            mailSender.send(message);

            logger.info(
                    "HTML email delivered successfully to {} via {}:{}",
                    maskEmail(to),
                    smtpHost,
                    smtpPort
            );

        } catch (MessagingException e) {

            logger.error(
                    "Unable to construct email message",
                    e
            );

            throw new IllegalArgumentException(
                    "Unable to construct email message: "
                            + safeMessage(e),
                    e
            );

        } catch (MailAuthenticationException e) {

            logger.error(
                    "SMTP authentication failed for {}",
                    maskEmail(smtpUsername),
                    e
            );

            throw smtpFailure(
                    "SMTP authentication failed. " +
                    "For Gmail, use a 16-character App Password.",
                    e
            );

        } catch (MailSendException e) {

            logger.error(
                    "SMTP server rejected email delivery to {}",
                    maskEmail(to),
                    e
            );

            throw smtpFailure(
                    "SMTP accepted the connection but rejected delivery. " +
                    "Check the sender address and recipient.",
                    e
            );

        } catch (MailException e) {

            logger.error(
                    "SMTP email delivery failed to {}",
                    maskEmail(to),
                    e
            );

            throw smtpFailure(
                    "Email delivery failed: "
                            + safeMessage(e),
                    e
            );

        } catch (Exception e) {

            logger.error(
                    "Unexpected email error while sending to {}",
                    maskEmail(to),
                    e
            );

            throw smtpFailure(
                    "Unexpected email delivery error: "
                            + safeMessage(e),
                    e
            );
        }
    }

    /**
     * Validates SMTP configuration before attempting delivery.
     */
    private void validateConfiguration() {

        if (!isConfigured()) {

            throw new IllegalStateException(
                    "Email delivery is not configured. " +
                    "Set SMTP_HOST, SMTP_PORT, SMTP_USERNAME " +
                    "and SMTP_PASSWORD in the backend environment."
            );
        }
    }

    /**
     * Determines the sender address.
     */
    private String resolveFrom() {

        String value =
                hasText(fromEmail)
                        ? fromEmail.trim()
                        : smtpUsername.trim();

        validateAddress(value);

        return value;
    }

    /**
     * Validates an email address.
     */
    private static void validateAddress(String value) {

        if (!hasText(value)) {

            throw new IllegalArgumentException(
                    "Email address cannot be empty."
            );
        }

        try {

            InternetAddress address =
                    new InternetAddress(value.trim());

            address.validate();

        } catch (AddressException e) {

            throw new IllegalArgumentException(
                    "Invalid email address: " + value,
                    e
            );
        }
    }

    /**
     * Prevents unsafe characters/path traversal
     * in attachment filenames.
     */
    private static String sanitizeAttachmentName(
            String name) {

        String safe =
                (name == null || name.isBlank())
                        ? "sentinelcore-report.pdf"
                        : name;

        safe = safe
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

        return safe.length() > 120
                ? safe.substring(0, 120)
                : safe;
    }

    /**
     * Requires a non-empty value.
     */
    private static void requireText(
            String value,
            String message) {

        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Checks whether a string contains usable text.
     */
    private static boolean hasText(String value) {

        return value != null
                && !value.trim().isEmpty();
    }

    /**
     * Masks an email address for safe logging.
     */
    private static String maskEmail(String email) {

        if (!hasText(email)) {
            return "***";
        }

        int at = email.indexOf('@');

        if (at <= 1) {

            return "***"
                    + (at >= 0
                    ? email.substring(at)
                    : "");
        }

        return email.charAt(0)
                + "***"
                + email.substring(at);
    }

    /**
     * Converts SMTP failures into a consistent
     * application-level exception.
     */
    private static RuntimeException smtpFailure(
            String message,
            Exception cause) {

        logger.error(message, cause);

        return new IllegalStateException(
                message,
                cause
        );
    }

    /**
     * Safely extracts an exception message.
     */
    private static String safeMessage(Exception e) {

        String message = e.getMessage();

        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
    }
}