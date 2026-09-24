package com.join.back.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Authenticates requests from a messenger mini app: validates the signed init
 * data sent in {@link #header} with the messenger's bot token and puts a
 * {@link MessengerAuthenticationToken} into the security context.
 */
@Slf4j
public abstract class MessengerAuthFilter extends OncePerRequestFilter {

    private final Messenger messenger;
    private final String header;
    private final String botToken;
    private final WebAppInitDataValidator initDataValidator;
    private final ObjectMapper objectMapper;

    protected MessengerAuthFilter(Messenger messenger, String header, String botToken,
                                  WebAppInitDataValidator initDataValidator, ObjectMapper objectMapper) {
        this.messenger = messenger;
        this.header = header;
        this.botToken = botToken;
        this.initDataValidator = initDataValidator;
        this.objectMapper = objectMapper;
    }

    protected abstract MessengerAuthenticationToken createToken(Long externalId, String firstName, String lastName, String username);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String initData = request.getHeader(header);

        if (initData != null && !initData.isEmpty()) {
            if (botToken == null || botToken.isBlank()) {
                log.warn("AUTH[{}]: init data received but the bot token is not configured", messenger);
            } else if (initDataValidator.validate(initData, botToken)) {
                Map<String, String> params = initDataValidator.parseInitData(initData);
                String userJson = params.get("user");

                if (userJson != null) {
                    JsonNode userNode = objectMapper.readTree(userJson);

                    Long externalId = userNode.get("id").asLong();
                    String firstName = userNode.has("first_name") ? userNode.get("first_name").asText() : "";
                    String lastName = userNode.has("last_name") ? userNode.get("last_name").asText() : "";
                    String username = userNode.has("username") ? userNode.get("username").asText() : "";

                    SecurityContextHolder.getContext()
                            .setAuthentication(createToken(externalId, firstName, lastName, username));
                    log.info("AUTH[{}]: OK — id={}, username={}", messenger, externalId, username);
                } else {
                    log.warn("AUTH[{}]: init data valid but 'user' field missing", messenger);
                }
            } else {
                log.warn("AUTH[{}]: init data validation FAILED for {} {}", messenger, request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
