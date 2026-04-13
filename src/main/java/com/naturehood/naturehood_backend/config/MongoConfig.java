package com.naturehood.naturehood_backend.config;

import com.naturehood.naturehood_backend.domain.Comment;
import com.naturehood.naturehood_backend.domain.Follow;
import com.naturehood.naturehood_backend.domain.Like;
import com.naturehood.naturehood_backend.domain.Post;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * MongoDB configuration.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);
    private static final int INDEX_INIT_MAX_ATTEMPTS = 5;
    private static final long INDEX_INIT_RETRY_DELAY_MS = 5000;

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mongoMappingContext;

    public MongoConfig(MongoTemplate mongoTemplate, MongoMappingContext mongoMappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mongoMappingContext = mongoMappingContext;
    }

    @PostConstruct
    public void initIndexes() {
        log.info("Ensuring MongoDB indexes...");
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);

        if (ensureIndexesWithRetry(resolver)) {
            log.info("MongoDB indexes ensured.");
            return;
        }

        log.warn("MongoDB was unreachable during startup index initialization. " +
                "Continuing startup; indexes will be ensured on next successful restart.");
    }

    private boolean ensureIndexesWithRetry(IndexResolver resolver) {
        for (int attempt = 1; attempt <= INDEX_INIT_MAX_ATTEMPTS; attempt++) {
            try {
                ensureIndexes(Post.class, resolver);
                ensureIndexes(Comment.class, resolver);
                ensureIndexes(Like.class, resolver);
                ensureIndexes(Follow.class, resolver);
                return true;
            } catch (RuntimeException ex) {
                if (attempt == INDEX_INIT_MAX_ATTEMPTS) {
                    log.warn("MongoDB index initialization failed after {} attempts. Last error: {}",
                            INDEX_INIT_MAX_ATTEMPTS, ex.getMessage());
                    return false;
                }

                log.warn("MongoDB index initialization attempt {}/{} failed: {}. Retrying in {} ms.",
                        attempt, INDEX_INIT_MAX_ATTEMPTS, ex.getMessage(), INDEX_INIT_RETRY_DELAY_MS);

                try {
                    Thread.sleep(INDEX_INIT_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("MongoDB index initialization retry interrupted. Continuing startup.");
                    return false;
                }
            }
        }

        return false;
    }

    private <T> void ensureIndexes(Class<T> entityClass, IndexResolver resolver) {
        IndexOperations indexOps = mongoTemplate.indexOps(entityClass);
        resolver.resolveIndexFor(entityClass).forEach(indexOps::ensureIndex);
    }

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
