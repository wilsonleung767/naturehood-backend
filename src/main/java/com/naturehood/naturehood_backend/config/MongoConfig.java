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

        ensureIndexes(Post.class, resolver);
        ensureIndexes(Comment.class, resolver);
        ensureIndexes(Like.class, resolver);
        ensureIndexes(Follow.class, resolver);

        log.info("MongoDB indexes ensured.");
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
