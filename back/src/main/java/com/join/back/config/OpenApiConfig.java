package com.join.back.config;

import com.join.back.security.MaxAuthFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Contract of the mini app API (/v3/api-docs). Admin panel and bot webhooks are internal
 * and excluded in application.yml; public methods opt out of auth with @SecurityRequirements.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
    static final String MAX_INIT_DATA = "maxInitData";

    @Bean
    public OpenAPI joinOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JOIN API")
                        .version("1.0.0")
                        .description("API мини-приложения JOIN в MAX: афиша, сохранённые события, совпадения, "
                                + "приглашения пойти вместе и чаты. Авторизация — JWT (вход по email) или подписанные "
                                + "данные запуска MAX. Без авторизации защищённые методы отвечают 403. "
                                + "Ошибки — JSON вида {\"error\": \"…\"}, иногда с полями message и code."))
                // Relative: calls go to the same domain the spec was loaded from.
                .servers(List.of(new Server().url("/").description("Домен мини-приложения")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Токен из POST /api/auth/login или /api/auth/register"))
                        .addSecuritySchemes(MAX_INIT_DATA, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(MaxAuthFilter.HEADER)
                                .description("window.WebApp.initData из MAX Bridge; подпись проверяется токеном бота")))
                // Either scheme is enough.
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .addSecurityItem(new SecurityRequirement().addList(MAX_INIT_DATA));
    }
}
