package com.santhosh.dashboard.controller;

import com.santhosh.dashboard.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class EmailController {
    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);
    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/email-status")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<Map<String, Object>> emailStatus() {
        boolean configured = emailService.isConfigured();
        return ResponseEntity.ok(Map.of(
                "configured", configured,
                "status", configured ? "READY" : "NOT_CONFIGURED",
                "message", configured
                        ? "SMTP credentials are configured."
                        : "Set SMTP_HOST, SMTP_PORT, SMTP_USERNAME and SMTP_PASSWORD before sending email."
        ));
    }

    @PostMapping("/send-email")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<Map<String, Object>> sendReportEmail(@RequestBody Map<String, Object> payload) {
        String to = asString(payload.get("to"));
        String subject = asString(payload.get("subject"));
        String body = asString(payload.get("body"));
        String reportType = asString(payload.get("reportType"));
        String fileName = asString(payload.get("fileName"));
        String attachmentBase64 = asString(payload.get("attachmentBase64"));

        if (to == null || to.isBlank()) return badRequest("Recipient address is required.");
        if (subject == null || subject.isBlank()) return badRequest("Subject is required.");

        try {
            byte[] attachmentData = decodeAttachment(attachmentBase64);
            String htmlBody = buildHtmlBody(reportType, body);
            emailService.sendHtmlEmailWithAttachment(to, subject, htmlBody, fileName, attachmentData);
            return ResponseEntity.ok(Map.of("success", true, "message", "Report emailed successfully."));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid email request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("Email service unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected email delivery failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Email delivery failed. Check server SMTP configuration and logs."));
        }
    }

    private static byte[] decodeAttachment(String value) {
        if (value == null || value.isBlank()) return null;
        String encoded = value.trim();
        int marker = encoded.indexOf(";base64,");
        if (marker >= 0) encoded = encoded.substring(marker + 8);
        return Base64.getDecoder().decode(encoded);
    }

    private static String buildHtmlBody(String reportType, String body) {
        return "<h3>SentinelCore SecureOps Report</h3>"
                + "<p><strong>Report Module:</strong> " + escapeHtml(reportType) + "</p>"
                + "<p>" + escapeHtml(body == null ? "" : body).replace("\n", "<br/>") + "</p>"
                + "<hr/><p style='font-size:0.8rem;color:#888;'>Automated operational report from SentinelCore SecureOps.</p>";
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
    }
}
