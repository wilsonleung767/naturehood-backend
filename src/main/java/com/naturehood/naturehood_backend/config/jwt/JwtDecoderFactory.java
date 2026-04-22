package com.naturehood.naturehood_backend.config.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Factory configuration class for creating JWT decoders.
 * Provides both HMAC (HS256) and JWKS (ES256/RS256) decoders based on configuration properties.
 */
@Configuration
public class JwtDecoderFactory {
    private static final Logger logger = LoggerFactory.getLogger(JwtDecoderFactory.class);

    /**
     * Creates an HMAC JWT decoder for HS256 tokens.
     * Only created when jwt.hmac.enabled is true (default) and jwt.hmac.secret is provided.
     */
    @Bean
    @ConditionalOnProperty(name = "jwt.hmac.enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder hmacJwtDecoder(
            @Value("${jwt.hmac.secret:}") String secretKey,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        if (!StringUtils.hasText(secretKey)) {
            logger.warn("JWT HMAC secret not provided. HMAC decoder will not be available.");
            return null;
        }

        logger.info("Creating HMAC JWT decoder for HS256 tokens");

        try {
            // Create secret key from the provided string
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

            // Create HMAC decoder
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();

            // Add issuer validation
            if (StringUtils.hasText(issuerUri)) {
                OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator));
                logger.debug("HMAC JWT decoder configured with issuer validation: {}", issuerUri);
            }

            logger.info("HMAC JWT decoder successfully created");
            return decoder;

        } catch (Exception e) {
            logger.error("Failed to create HMAC JWT decoder: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not create HMAC JWT decoder", e);
        }
    }

    /**
     * Creates a JWKS JWT decoder for ES256/RS256 tokens.
     * Uses the existing JWKS URI and issuer URI configuration.
     */
    @Bean
    public JwtDecoder jwksJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        logger.info("Creating JWKS JWT decoder for ES256/RS256 tokens");

        try {
            // Create JWKS decoder with the same configuration as the original SecurityConfig
            NimbusJwtDecoder jwkDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                    .jwsAlgorithms(algorithms -> {
                        algorithms.add(SignatureAlgorithm.ES256);
                        algorithms.add(SignatureAlgorithm.RS256);
                    })
                    .build();

            // Add issuer validation
            if (StringUtils.hasText(issuerUri)) {
                OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
                jwkDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator));
                logger.debug("JWKS JWT decoder configured with issuer validation: {}", issuerUri);
            }

            logger.info("JWKS JWT decoder successfully created with URI: {}", jwkSetUri);
            return jwkDecoder;

        } catch (Exception e) {
            logger.error("Failed to create JWKS JWT decoder: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not create JWKS JWT decoder", e);
        }
    }

    /**
     * Creates the primary DualJwtDecoder that combines HMAC and JWKS decoders.
     * The decoder strategy is configurable via jwt.decoder.strategy property.
     */
    @Bean
    @Primary
    public DualJwtDecoder dualJwtDecoder(
            @Autowired(required = false) @Qualifier("hmacJwtDecoder") JwtDecoder hmacDecoder,
            @Qualifier("jwksJwtDecoder") JwtDecoder jwksDecoder,
            @Value("${jwt.decoder.strategy:hmac-first}") String strategy
    ) {
        logger.info("Creating DualJwtDecoder with strategy: {}", strategy);

        // Validate strategy
        if (!isValidStrategy(strategy)) {
            logger.warn("Invalid JWT decoder strategy '{}', falling back to 'hmac-first'", strategy);
            strategy = "hmac-first";
        }

        // Log decoder availability
        logger.info("HMAC decoder available: {}", hmacDecoder != null);
        logger.info("JWKS decoder available: {}", jwksDecoder != null);

        // Ensure at least one decoder is available
        if (hmacDecoder == null && jwksDecoder == null) {
            throw new IllegalStateException("No JWT decoders available. At least one decoder must be configured.");
        }

        DualJwtDecoder dualDecoder = new DualJwtDecoder(hmacDecoder, jwksDecoder, strategy);
        logger.info("DualJwtDecoder created successfully: {}", dualDecoder.getDecoderInfo());

        return dualDecoder;
    }

    /**
     * Validates the JWT decoder strategy configuration.
     */
    private boolean isValidStrategy(String strategy) {
        return "hmac-first".equals(strategy) ||
               "jwks-first".equals(strategy) ||
               "hmac-only".equals(strategy) ||
               "jwks-only".equals(strategy);
    }
}