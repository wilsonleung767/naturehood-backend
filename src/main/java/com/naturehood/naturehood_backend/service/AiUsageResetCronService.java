package com.naturehood.naturehood_backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled job that resets AI usage counters in Supabase (Postgres).
 *
 * Mirrors the Express cron behavior:
 * - Target table: user_ai_usage
 * - Updated fields: food_analysis_count=0, work_out_log_count=0
 * - Configurable cron expression + timezone via env-backed properties
 */
@Service
public class AiUsageResetCronService implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AiUsageResetCronService.class);
    private static final String USER_AI_USAGE_PATH = "/rest/v1/user_ai_usage";
    private static final String RESET_PAYLOAD = "{\"food_analysis_count\":0,\"work_out_log_count\":0}";

    private final HttpClient httpClient;
    private final AtomicBoolean isRunning;

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key:}")
    private String supabaseServiceRoleKey;

    @Value("${ai.usage.reset.cron:0 0 * * *}")
    private String cronExpression;

    @Value("${ai.usage.reset.timezone:UTC}")
    private String timezone;

    private volatile boolean enabled;
    private volatile String springCronExpression;

    public AiUsageResetCronService() {
        this.httpClient = HttpClient.newHttpClient();
        this.isRunning = new AtomicBoolean(false);
    }

    @PostConstruct
    void validateAndLogSchedule() {
        springCronExpression = toSpringCron(cronExpression);

        if (supabaseUrl == null || supabaseUrl.isBlank() ||
                supabaseServiceRoleKey == null || supabaseServiceRoleKey.isBlank()) {
            enabled = false;
            log.warn("[ai-usage-cron] Disabled: SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY is missing");
            return;
        }

        enabled = true;

        log.info("[ai-usage-cron] Scheduled reset with '{}' (spring='{}') in timezone '{}'",
                cronExpression, springCronExpression, timezone);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::resetUsageCounters,
                triggerContext -> new CronTrigger(springCronExpression, ZoneId.of(timezone)).nextExecution(triggerContext)
        );
    }

    public void resetUsageCounters() {
        if (!enabled) {
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            String normalizedBaseUrl = supabaseUrl.endsWith("/")
                    ? supabaseUrl.substring(0, supabaseUrl.length() - 1)
                    : supabaseUrl;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl + USER_AI_USAGE_PATH))
                    .header("apikey", supabaseServiceRoleKey)
                    .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(RESET_PAYLOAD))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                log.info("[ai-usage-cron] Reset user_ai_usage counters to 0");
            } else {
                log.error("[ai-usage-cron] Failed to reset counters: status={}, body={}", status, response.body());
            }
        } catch (Exception e) {
            log.error("[ai-usage-cron] Failed to reset counters: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }
    }

    private String toSpringCron(String expression) {
        String normalized = expression == null ? "" : expression.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Invalid AI usage reset cron expression: " + expression);
        }

        String[] parts = normalized.split(" ");
        String candidate;
        if (parts.length == 5) {
            candidate = "0 " + normalized;
        } else if (parts.length == 6) {
            candidate = normalized;
        } else {
            throw new IllegalArgumentException("Invalid AI usage reset cron expression: " + expression);
        }

        if (!CronExpression.isValidExpression(candidate)) {
            throw new IllegalArgumentException("Invalid AI usage reset cron expression: " + expression);
        }

        return candidate;
    }
}
