package com.naturehood.naturehood_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class SupabaseProfileService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseProfileService.class);

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final RestTemplate restTemplate;

    public SupabaseProfileService(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.service-role-key:}") String serviceRoleKey) {
        this.supabaseUrl = supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Returns the display name for a user: prefers {@code name}, falls back to
     * {@code username}, and uses {@code "Someone"} if the lookup fails or is
     * misconfigured.
     */
    public String getDisplayName(String userId) {
        if (supabaseUrl.isBlank() || serviceRoleKey.isBlank() || userId == null || userId.isBlank()) {
            return "Someone";
        }

        try {
            String url = supabaseUrl + "/rest/v1/profiles?id=eq." + userId + "&select=username,name";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceRoleKey);
            headers.set("apikey", serviceRoleKey);

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);

            if (response.getBody() == null || response.getBody().isEmpty()) {
                return "Someone";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) response.getBody().get(0);

            String name = (String) profile.get("name");
            if (name != null && !name.isBlank()) return name;

            String username = (String) profile.get("username");
            if (username != null && !username.isBlank()) return username;

            return "Someone";

        } catch (Exception e) {
            log.warn("Failed to fetch profile for user {}: {}", userId, e.getMessage());
            return "Someone";
        }
    }
}
