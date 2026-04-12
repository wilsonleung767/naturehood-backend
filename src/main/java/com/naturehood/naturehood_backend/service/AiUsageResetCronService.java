package com.naturehood.naturehood_backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * Scheduled job that resets AI usage counters in MongoDB.
 *
 * Mirrors the Express cron behavior:
 * - Target collection: user_ai_usage
 * - Updated fields: food_analysis_count=0, work_out_log_count=0
 * - Configurable cron expression + timezone via env-backed properties
 */
@Service
public class AiUsageResetCronService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageResetCronService.class);
    private static final String COLLECTION = "user_ai_usage";

    private final MongoTemplate mongoTemplate;

    @Value("${ai.usage.reset.cron:0 0 0 * * *}")
    private String cronExpression;

    @Value("${ai.usage.reset.timezone:UTC}")
    private String timezone;

    public AiUsageResetCronService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    void validateAndLogSchedule() {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("Invalid AI usage reset cron expression: " + cronExpression);
        }

        log.info("[ai-usage-cron] Scheduled reset with '{}' in timezone '{}'", cronExpression, timezone);
    }

    @Scheduled(cron = "${ai.usage.reset.cron:0 0 0 * * *}", zone = "${ai.usage.reset.timezone:UTC}")
    public void resetUsageCounters() {
        try {
            Query allRows = new Query();
            Update reset = new Update()
                    .set("food_analysis_count", 0)
                    .set("work_out_log_count", 0);

            var result = mongoTemplate.updateMulti(allRows, reset, COLLECTION);
            log.info("[ai-usage-cron] Reset user_ai_usage counters to 0 (matched={}, modified={})",
                    result.getMatchedCount(), result.getModifiedCount());
        } catch (Exception e) {
            log.error("[ai-usage-cron] Failed to reset counters: {}", e.getMessage(), e);
        }
    }
}
