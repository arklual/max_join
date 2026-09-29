package com.join.back.config;

/** Swagger sections, in the order of the user's path; descriptions are in {@link OpenApiConfig}. */
public final class ApiDocs {

    public static final String AUTH = "Вход и регистрация";
    public static final String PROFILE = "Профиль";
    public static final String AFISHA = "Афиша";
    public static final String LIKES = "Сохранённые события";
    public static final String MATCHES = "Совпадения и приглашения";
    public static final String CHATS = "Личные чаты";
    public static final String OUTINGS = "Мои походы";
    public static final String GROUPS = "Компании на событие";
    public static final String FRIEND_GROUPS = "Группы друзей";
    public static final String NOTIFICATIONS = "Уведомления";
    public static final String BLOCKS = "Чёрный список";
    public static final String SUPPORT = "Поддержка";
    public static final String DICTIONARIES = "Справочники";
    public static final String SERVICE = "Служебное";

    private ApiDocs() {
    }
}
