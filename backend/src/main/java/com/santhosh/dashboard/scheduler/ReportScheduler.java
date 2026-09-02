package com.santhosh.dashboard.scheduler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.santhosh.dashboard.model.Incident;
import com.santhosh.dashboard.model.ReportSchedule;
import com.santhosh.dashboard.repository.ReportScheduleRepository;
import com.santhosh.dashboard.service.EmailService;
import com.santhosh.dashboard.service.IncidentService;

@Component
public class ReportScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(ReportScheduler.class);

    /**
     * All scheduled report times are interpreted in Indian Standard Time.
     */
    private static final ZoneId ZONE =
            ZoneId.of("Asia/Kolkata");

    private final ReportScheduleRepository repository;
    private final EmailService emailService;
    private final IncidentService incidentService;

    public ReportScheduler(
            ReportScheduleRepository repository,
            EmailService emailService,
            IncidentService incidentService) {

        this.repository = repository;
        this.emailService = emailService;
        this.incidentService = incidentService;
    }

    /**
     * Checks for due scheduled reports every minute.
     *
     * The cron itself is explicitly configured for Asia/Kolkata,
     * so scheduled delivery is based on IST rather than UTC.
     */
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Kolkata"
    )
    public void executeDueSchedules() {

        LocalDateTime now = LocalDateTime
                .now(ZONE)
                .withSecond(0)
                .withNano(0);

        log.debug(
                "Checking scheduled reports at {} IST",
                now
        );

        List<ReportSchedule> schedules;

        try {

            schedules =
                    repository
                            .findByActiveTrueOrderByLocalTimeAsc();

        } catch (Exception e) {

            log.error(
                    "Unable to load scheduled reports from database",
                    e
            );

            return;
        }

        if (schedules == null || schedules.isEmpty()) {

            log.debug(
                    "No active scheduled reports found"
            );

            return;
        }

        for (ReportSchedule schedule : schedules) {

            try {

                if (!isDue(schedule, now)) {
                    continue;
                }

                log.info(
                        "Scheduled report {} is due. " +
                        "Recipient: {}, Time: {} IST, Frequency: {}",
                        schedule.getId(),
                        maskEmail(schedule.getRecipient()),
                        schedule.getLocalTime(),
                        schedule.getFrequency()
                );

                send(schedule, now);

                /*
                 * Only mark the schedule as executed AFTER
                 * successful email delivery.
                 */
                schedule.setLastRunAt(now);

                repository.save(schedule);

                log.info(
                        "Scheduled report {} delivered successfully " +
                        "to {} at {} IST",
                        schedule.getId(),
                        maskEmail(schedule.getRecipient()),
                        now
                );

            } catch (Exception e) {

                /*
                 * IMPORTANT:
                 * We intentionally do NOT update lastRunAt when
                 * delivery fails. This allows the failure to be
                 * retried by the next appropriate execution.
                 */
                log.error(
                        "Scheduled report {} failed for {}",
                        schedule.getId(),
                        maskEmail(schedule.getRecipient()),
                        e
                );
            }
        }
    }

    /**
     * Determines whether a scheduled report should execute now.
     *
     * Supported frequencies:
     * DAILY
     * WEEKLY
     * MONTHLY
     */
    private boolean isDue(
            ReportSchedule schedule,
            LocalDateTime now) {

        if (schedule == null) {
            return false;
        }

        if (!schedule.isActive()) {
            return false;
        }

        if (schedule.getLocalTime() == null) {
            log.warn(
                    "Schedule {} has no configured local time",
                    schedule.getId()
            );

            return false;
        }

        if (schedule.getRecipient() == null
                || schedule.getRecipient().isBlank()) {

            log.warn(
                    "Schedule {} has no recipient",
                    schedule.getId()
            );

            return false;
        }

        if (schedule.getFrequency() == null) {

            log.warn(
                    "Schedule {} has no frequency",
                    schedule.getId()
            );

            return false;
        }

        /*
         * Compare only hour and minute.
         *
         * This is safer than LocalTime.equals() because a database
         * value may contain seconds/nanoseconds while the scheduler
         * intentionally works at minute precision.
         */
        LocalTime scheduledTime =
                schedule.getLocalTime();

        if (scheduledTime.getHour() != now.getHour()
                || scheduledTime.getMinute() != now.getMinute()) {

            return false;
        }

        /*
         * Prevent duplicate delivery during the same minute.
         */
        if (schedule.getLastRunAt() != null) {

            LocalDateTime lastRun =
                    schedule.getLastRunAt()
                            .withSecond(0)
                            .withNano(0);

            if (lastRun.equals(now)) {

                log.debug(
                        "Schedule {} already executed at {} IST",
                        schedule.getId(),
                        now
                );

                return false;
            }
        }

        return switch (schedule.getFrequency()) {

            case DAILY -> true;

            case WEEKLY -> {

                Integer configuredDay =
                        schedule.getDayOfWeek();

                /*
                 * Java DayOfWeek:
                 * Monday = 1
                 * Tuesday = 2
                 * ...
                 * Sunday = 7
                 */
                yield configuredDay != null
                        && configuredDay
                        == now.getDayOfWeek().getValue();
            }

            case MONTHLY -> {

                Integer configuredDay =
                        schedule.getDayOfMonth();

                yield configuredDay != null
                        && configuredDay
                        == now.getDayOfMonth();
            }
        };
    }

    /**
     * Generates and sends the scheduled security report.
     */
    private void send(
            ReportSchedule schedule,
            LocalDateTime now) {

        List<Incident> incidents =
                incidentService.getAllIncidents();

        if (incidents == null) {
            incidents = List.of();
        }

        long openIncidents =
                incidents.stream()
                        .filter(incident ->
                                incident.getStatus() == null
                                        || !"RESOLVED".equalsIgnoreCase(
                                        incident.getStatus()
                                )
                        )
                        .count();

        long criticalIncidents =
                incidents.stream()
                        .filter(incident ->
                                "CRITICAL".equalsIgnoreCase(
                                        incident.getSeverity()
                                )
                        )
                        .count();

        String reportType =
                schedule.getReportType() == null
                        ? "Security Report"
                        : schedule.getReportType();

        String recipient =
                schedule.getRecipient().trim();

        String generatedAt =
                now.toString();

        String body =
                "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<title>SentinelCore Scheduled Report</title>" +
                "</head>" +
                "<body style=\"font-family:Arial,sans-serif;" +
                "line-height:1.6;color:#222;\">" +

                "<h2>SentinelCore SecureOps Scheduled Report</h2>" +

                "<p>" +
                "<strong>Report:</strong> "
                + escape(reportType) +
                "</p>" +

                "<p>" +
                "<strong>Generated:</strong> "
                + escape(generatedAt) +
                " IST" +
                "</p>" +

                "<hr>" +

                "<h3>Security Summary</h3>" +

                "<ul>" +

                "<li>" +
                "<strong>Total Incidents:</strong> "
                + incidents.size() +
                "</li>" +

                "<li>" +
                "<strong>Open Incidents:</strong> "
                + openIncidents +
                "</li>" +

                "<li>" +
                "<strong>Critical Incidents:</strong> "
                + criticalIncidents +
                "</li>" +

                "</ul>" +

                "<hr>" +

                "<p style=\"color:#666;\">" +
                "This delivery was generated automatically " +
                "by the SentinelCore SecureOps scheduler." +
                "</p>" +

                "<p style=\"color:#666;\">" +
                "Timezone: Asia/Kolkata (IST)" +
                "</p>" +

                "</body>" +
                "</html>";

        String subject =
                "SentinelCore scheduled "
                        + reportType;

        /*
         * No attachment is generated here.
         * The report is delivered as an HTML email.
         */
        emailService.sendHtmlEmailWithAttachment(
                recipient,
                subject,
                body,
                null,
                null
        );
    }

    /**
     * Basic HTML escaping to prevent report data
     * from being interpreted as HTML.
     */
    private static String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Masks email addresses in application logs.
     */
    private static String maskEmail(String email) {

        if (email == null || email.isBlank()) {
            return "***";
        }

        int at =
                email.indexOf('@');

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
}