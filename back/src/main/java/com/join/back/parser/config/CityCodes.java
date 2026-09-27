package com.join.back.parser.config;

import java.util.Map;

/**
 * How each event source names a city. JOIN works with Russian city names ({@code parser.cities});
 * a source that doesn't cover a city simply has no code for it.
 * Adding a city = adding its codes here (if the source needs codes) and its name to {@code JOIN_CITIES}.
 */
public final class CityCodes {

    /** KudaGo {@code location} slugs — see {@code /public-api/v1.4/locations/}. */
    private static final Map<String, String> KUDAGO = Map.of(
            "Москва", "msk",
            "Санкт-Петербург", "spb",
            "Казань", "kzn",
            "Екатеринбург", "ekb",
            "Нижний Новгород", "nnv");

    /** Yandex Afisha URL slugs: {@code afisha.yandex.ru/<slug>}. */
    private static final Map<String, String> YANDEX_AFISHA = Map.of(
            "Москва", "moscow",
            "Санкт-Петербург", "saint-petersburg",
            "Казань", "kazan",
            "Екатеринбург", "yekaterinburg",
            "Нижний Новгород", "nizhny-novgorod",
            "Новосибирск", "novosibirsk");

    private CityCodes() {
    }

    public static String kudago(String city) {
        return KUDAGO.get(city);
    }

    public static String yandexAfisha(String city) {
        return YANDEX_AFISHA.get(city);
    }

    /** City name for a KudaGo slug, or null. */
    public static String cityForKudago(String slug) {
        return KUDAGO.entrySet().stream()
                .filter(e -> e.getValue().equals(slug))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
