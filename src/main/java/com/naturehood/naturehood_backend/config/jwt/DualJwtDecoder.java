package com.naturehood.naturehood_backend.config.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Dual JWT decoder that supports both HMAC (HS256) and JWKS (ES256/RS256) token validation.
 * Implements a fallback strategy where it tries the primary decoder first, then falls back
 * to the secondary decoder if the primary fails.
 */
@Component
public class DualJwtDecoder implements JwtDecoder {
    private static final Logger logger = LoggerFactory.getLogger(DualJwtDecoder.class);

    private final JwtDecoder hmacDecoder;
    private final JwtDecoder jwksDecoder;
    private final String strategy;

    public DualJwtDecoder(JwtDecoder hmacDecoder, JwtDecoder jwksDecoder, String strategy) {
        this.hmacDecoder = hmacDecoder;
        this.jwksDecoder = jwksDecoder;
        this.strategy = strategy != null ? strategy : "hmac-first";

        logger.info("DualJwtDecoder initialized with strategy: {}", this.strategy);
        logger.debug("HMAC decoder available: {}", hmacDecoder != null);
        logger.debug("JWKS decoder available: {}", jwksDecoder != null);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        if (token == null || token.trim().isEmpty()) {
            throw new JwtValidationException("JWT token cannot be null or empty", Collections.emptyList());
        }

        return switch (strategy) {
            case "hmac-first" -> decodeWithFallback(hmacDecoder, jwksDecoder, token, "HMAC", "JWKS");
            case "jwks-first" -> decodeWithFallback(jwksDecoder, hmacDecoder, token, "JWKS", "HMAC");
            case "hmac-only" -> {
                if (hmacDecoder == null) {
                    throw new JwtValidationException("HMAC decoder not available", Collections.emptyList());
                }
                yield hmacDecoder.decode(token);
            }
            case "jwks-only" -> {
                if (jwksDecoder == null) {
                    throw new JwtValidationException("JWKS decoder not available", Collections.emptyList());
                }
                yield jwksDecoder.decode(token);
            }
            default -> decodeWithFallback(hmacDecoder, jwksDecoder, token, "HMAC", "JWKS");
        };
    }

    /**
     * Attempts to decode the JWT token using the primary decoder, falling back to the secondary
     * decoder if the primary fails.
     */
    private Jwt decodeWithFallback(JwtDecoder primary, JwtDecoder fallback, String token,
                                   String primaryName, String fallbackName) throws JwtException {
        // Try primary decoder first
        if (primary != null) {
            try {
                Jwt jwt = primary.decode(token);
                logger.debug("JWT successfully decoded with {} decoder", primaryName);
                return jwt;
            } catch (JwtException e) {
                logger.debug("{} decoder failed: {}", primaryName, e.getMessage());

                // Try fallback decoder
                if (fallback != null) {
                    try {
                        Jwt jwt = fallback.decode(token);
                        logger.info("JWT decoded with {} decoder after {} decoder failed", fallbackName, primaryName);
                        return jwt;
                    } catch (JwtException fallbackException) {
                        logger.error("Both {} and {} decoders failed. Primary error: {}, Fallback error: {}",
                            primaryName, fallbackName, e.getMessage(), fallbackException.getMessage());

                        // Throw the original primary decoder exception as it's more relevant
                        throw e;
                    }
                } else {
                    logger.error("{} decoder failed and no {} decoder available: {}", primaryName, fallbackName, e.getMessage());
                    throw e;
                }
            }
        } else if (fallback != null) {
            // Primary decoder not available, use fallback only
            logger.debug("{} decoder not available, using {} decoder only", primaryName, fallbackName);
            return fallback.decode(token);
        } else {
            // Neither decoder available
            throw new JwtValidationException("No JWT decoders available", Collections.emptyList());
        }
    }

    /**
     * Returns information about the current decoder configuration for debugging purposes.
     */
    public String getDecoderInfo() {
        return String.format("DualJwtDecoder[strategy=%s, hmac=%s, jwks=%s]",
            strategy,
            hmacDecoder != null ? "available" : "unavailable",
            jwksDecoder != null ? "available" : "unavailable");
    }
}