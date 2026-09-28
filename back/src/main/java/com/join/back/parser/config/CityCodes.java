package com.join.back.parser.config;

import java.time.ZoneId;
import java.util.Map;

/**
 * How each event source names a city. JOIN works with Russian city names ({@code parser.cities});
 * a source that doesn't cover a city simply has no code for it.
 * Adding a city = adding its codes here (if the source needs codes) and its name to {@code JOIN_CITIES}.
 */
public final class CityCodes {

    /** KudaGo {@code location} slugs — see {@code /public-api/v1.4/locations/} (KudaGo covers only these). */
    private static final Map<String, String> KUDAGO = Map.of(
            "Москва", "msk",
            "Санкт-Петербург", "spb",
            "Казань", "kzn",
            "Екатеринбург", "ekb",
            "Нижний Новгород", "nnv");

    /** Yandex Afisha URL slugs: {@code afisha.yandex.ru/<slug>}. */
    private static final Map<String, String> YANDEX_AFISHA = Map.ofEntries(
            Map.entry("Москва", "moscow"),
            Map.entry("Санкт-Петербург", "saint-petersburg"),
            Map.entry("Новосибирск", "novosibirsk"),
            Map.entry("Екатеринбург", "yekaterinburg"),
            Map.entry("Казань", "kazan"),
            Map.entry("Нижний Новгород", "nizhny-novgorod"),
            Map.entry("Красноярск", "krasnoyarsk"),
            Map.entry("Челябинск", "chelyabinsk"),
            Map.entry("Самара", "samara"),
            Map.entry("Уфа", "ufa"),
            Map.entry("Ростов-на-Дону", "rostov-na-donu"),
            Map.entry("Краснодар", "krasnodar"),
            Map.entry("Омск", "omsk"),
            Map.entry("Воронеж", "voronezh"),
            Map.entry("Пермь", "perm"),
            Map.entry("Волгоград", "volgograd"));

    /** Kassir.ru city hosts: {@code <host>} for its API {@code domain} parameter. */
    private static final Map<String, String> KASSIR = Map.ofEntries(
            Map.entry("Москва", "msk.kassir.ru"),
            Map.entry("Санкт-Петербург", "spb.kassir.ru"),
            Map.entry("Новосибирск", "nsk.kassir.ru"),
            Map.entry("Екатеринбург", "ekb.kassir.ru"),
            Map.entry("Казань", "kzn.kassir.ru"),
            Map.entry("Нижний Новгород", "nn.kassir.ru"),
            Map.entry("Красноярск", "krs.kassir.ru"),
            Map.entry("Челябинск", "chel.kassir.ru"),
            Map.entry("Самара", "smr.kassir.ru"),
            Map.entry("Уфа", "ufa.kassir.ru"),
            Map.entry("Ростов-на-Дону", "rnd.kassir.ru"),
            Map.entry("Краснодар", "krd.kassir.ru"),
            Map.entry("Омск", "omsk.kassir.ru"),
            Map.entry("Воронеж", "vrn.kassir.ru"),
            Map.entry("Пермь", "perm.kassir.ru"),
            Map.entry("Волгоград", "vlg.kassir.ru"));

    /** Local time zones: sources that give an instant (KudaGo) must show the city's wall-clock time. */
    private static final Map<String, ZoneId> ZONES = Map.ofEntries(
            Map.entry("Самара", ZoneId.of("Europe/Samara")),
            Map.entry("Екатеринбург", ZoneId.of("Asia/Yekaterinburg")),
            Map.entry("Челябинск", ZoneId.of("Asia/Yekaterinburg")),
            Map.entry("Пермь", ZoneId.of("Asia/Yekaterinburg")),
            Map.entry("Уфа", ZoneId.of("Asia/Yekaterinburg")),
            Map.entry("Омск", ZoneId.of("Asia/Omsk")),
            Map.entry("Новосибирск", ZoneId.of("Asia/Novosibirsk")),
            Map.entry("Красноярск", ZoneId.of("Asia/Krasnoyarsk")),
            Map.entry("Волгоград", ZoneId.of("Europe/Volgograd")));

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private CityCodes() {
    }

    public static String kudago(String city) {
        return KUDAGO.get(city);
    }

    public static String yandexAfisha(String city) {
        return YANDEX_AFISHA.get(city);
    }

    public static String kassir(String city) {
        return KASSIR.get(city);
    }

    /** City name for a KudaGo slug, or null. */
    public static String cityForKudago(String slug) {
        return KUDAGO.entrySet().stream()
                .filter(e -> e.getValue().equals(slug))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** The city's time zone (Moscow time for cities that use it or are unknown). */
    public static ZoneId zone(String city) {
        return city == null ? MOSCOW : ZONES.getOrDefault(city, MOSCOW);
    }
}
