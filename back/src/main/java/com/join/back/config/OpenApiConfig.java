package com.join.back.config;

import com.join.back.model.dto.ErrorResponse;
import com.join.back.security.MaxAuthFilter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Contract of the mini app API (/v3/api-docs, Swagger UI at /swagger-ui/index.html).
 * Admin panel and bot webhooks are internal and excluded in application.yml; public methods
 * opt out of auth with @SecurityRequirements. Operations are described next to the code with
 * @Tag/@Operation; standard error responses are added here by one rule for all of them.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
    static final String MAX_INIT_DATA = "maxInitData";

    private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";
    private static final String BAD_REQUEST = "BadRequest";
    private static final String FORBIDDEN = "Forbidden";
    private static final String NOT_FOUND = "NotFound";

    private static final String DESCRIPTION = """
            API мини-приложения **JOIN** в MAX: афиша города, события по Пушкинской карте и поиск компании, \
            чтобы пойти вместе.

            ### Основной сценарий
            1. **Вход** — `POST /api/auth/login` (или регистрация) возвращает `token`.
            2. **Афиша** — `GET /api/events?city=Казань`, фильтр `pushkinCard=true`.
            3. **Сохранить событие** — `POST /api/events/{id}/like`. Если его уже сохранил подходящий человек, \
            в ответе будет совпадение.
            4. **Позвать пойти вместе** — `POST /api/matches/{id}/request`, второй отвечает \
            `POST /api/matches/{id}/accept`. Только после этого открывается чат.
            5. **Чат** — `GET` / `POST /api/chats/{chatId}/messages`.

            ### Как попробовать
            Получите токен через `POST /api/auth/login`, нажмите **Authorize** и вставьте его в `bearerAuth`. \
            Внутри MAX вместо токена приложение передаёт заголовок `X-Max-Init-Data`.

            ### Ответы
            * Без авторизации защищённые методы отвечают `403` с пустым телом.
            * Ошибки — JSON `ErrorResponse`: `{"error": "текст для пользователя", "code": "…"}`.
            * Списки с `page`, `size`, `sort` возвращают страницу: `content`, `totalElements`, `number`, `size`.
            * Даты — `YYYY-MM-DD`, время — `HH:mm:ss`, местное время города события.

            Обязательные проверки основного сценария — `DATA-API.yaml` в репозитории.""";

    private static final List<Tag> TAGS = List.of(
            tag(ApiDocs.AUTH, "Регистрация и вход: по email и паролю, через MAX на сайте, привязка MAX и email к одному аккаунту"),
            tag(ApiDocs.PROFILE, "Свой профиль, профили других пользователей, настройки поиска компании, согласие и удаление аккаунта"),
            tag(ApiDocs.AFISHA, "События города из нескольких афиш: лента с фильтрами, подборка по интересам, карточка события"),
            tag(ApiDocs.LIKES, "Сохранение событий. Совпадение появляется, когда то же событие сохраняет подходящий человек"),
            tag(ApiDocs.MATCHES, "Найденные напарники и приглашения «пойдём вместе?». Чат открывается только после согласия"),
            tag(ApiDocs.CHATS, "Переписка с напарником, подсказки для первой фразы и вопрос «Сходили вместе?»"),
            tag(ApiDocs.OUTINGS, "Ближайшие события, на которые уже есть компания"),
            tag(ApiDocs.GROUPS, "Открытые компании из нескольких человек на конкретное событие и их чаты"),
            tag(ApiDocs.FRIEND_GROUPS, "Группы знакомых: вступление по коду или QR-коду, общий чат, события, которые сохранили все"),
            tag(ApiDocs.NOTIFICATIONS, "Уведомления внутри приложения; те же события бот присылает в MAX"),
            tag(ApiDocs.BLOCKS, "Личный чёрный список: с заблокированным нет совпадений и переписки"),
            tag(ApiDocs.SUPPORT, "Чат с поддержкой"),
            tag(ApiDocs.DICTIONARIES, "Города, вузы и настройки клиента — без авторизации"),
            tag(ApiDocs.SERVICE, "Проверка работоспособности"));

    @Bean
    public OpenAPI joinOpenAPI() {
        Components components = new Components()
                .addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Токен из POST /api/auth/login или /api/auth/register"))
                .addSecuritySchemes(MAX_INIT_DATA, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(MaxAuthFilter.HEADER)
                        .description("window.WebApp.initData из MAX Bridge; подпись проверяется токеном бота"))
                .addResponses(BAD_REQUEST, errorResponse("Некорректные данные запроса",
                        Map.of("error", "JOIN доступен с 14 лет", "code", "VALIDATION_FAILED")))
                .addResponses(FORBIDDEN, new ApiResponse()
                        .description("Нет авторизации (тело пустое) или нет доступа к объекту"))
                .addResponses(NOT_FOUND, errorResponse("Объект не найден или недоступен",
                        Map.of("error", "Event not found with id: 42")));
        ModelConverters.getInstance().read(ErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info().title("JOIN API").version("1.0.0").description(DESCRIPTION))
                // Relative: calls go to the same domain the spec was loaded from.
                .servers(List.of(new Server().url("/").description("Домен мини-приложения")))
                .tags(TAGS)
                .components(components)
                // Either scheme is enough.
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .addSecurityItem(new SecurityRequirement().addList(MAX_INIT_DATA));
    }

    /** Errors declared on a method with {@link ApiError}. */
    @Bean
    public OperationCustomizer declaredErrorResponses() {
        return (operation, handlerMethod) -> {
            for (ApiError error : handlerMethod.getMethod().getAnnotationsByType(ApiError.class)) {
                ApiResponse response = new ApiResponse().description(error.description());
                if (error.code().startsWith("4")) {
                    response.setContent(errorContent());
                }
                operation.getResponses().addApiResponse(error.code(), response);
            }
            return operation;
        };
    }

    /**
     * The same error responses for every operation, by one rule: 400 when there is a body to validate,
     * 403 when auth is required, 404 when the path points at an object. Responses are listed by code.
     */
    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            ApiResponses responses = operation.getResponses();
            if (operation.getRequestBody() != null) {
                responses.putIfAbsent("400", ref(BAD_REQUEST));
            }
            if (!isPublic(operation)) {
                responses.putIfAbsent("403", ref(FORBIDDEN));
            }
            if (hasPathParameters(operation)) {
                responses.putIfAbsent("404", ref(NOT_FOUND));
            }
            ApiResponses sorted = new ApiResponses();
            new TreeMap<>(responses).forEach(sorted::addApiResponse);
            operation.setResponses(sorted);
        }));
    }

    private static boolean isPublic(Operation operation) {
        return operation.getSecurity() != null && operation.getSecurity().isEmpty();
    }

    private static boolean hasPathParameters(Operation operation) {
        List<Parameter> parameters = operation.getParameters();
        return parameters != null && parameters.stream().anyMatch(p -> "path".equals(p.getIn()));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static ApiResponse ref(String name) {
        return new ApiResponse().$ref("#/components/responses/" + name);
    }

    private static ApiResponse errorResponse(String description, Map<String, String> example) {
        Content content = errorContent();
        content.get(org.springframework.http.MediaType.APPLICATION_JSON_VALUE).setExample(example);
        return new ApiResponse().description(description).content(content);
    }

    private static Content errorContent() {
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new Schema<ErrorResponse>().$ref(ERROR_SCHEMA)));
    }
}
