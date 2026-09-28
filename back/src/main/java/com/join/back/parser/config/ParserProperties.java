package com.join.back.parser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "parser")
public class ParserProperties {

    private boolean enabled = true;
    /**
     * Cities JOIN serves (Russian names, env {@code JOIN_CITIES}). Every source loads events for these
     * cities that it covers (codes in {@link CityCodes}); the afisha is filtered by the user's city among them.
     */
    private List<String> cities = List.of("Москва", "Санкт-Петербург", "Новосибирск", "Екатеринбург", "Казань",
            "Нижний Новгород", "Красноярск", "Челябинск", "Самара", "Уфа", "Ростов-на-Дону", "Краснодар", "Омск",
            "Воронеж", "Пермь", "Волгоград");
    private ScheduleConfig schedule = new ScheduleConfig();
    private SourcesConfig sources = new SourcesConfig();
    private DefaultsConfig defaults = new DefaultsConfig();

    @Data
    public static class ScheduleConfig {
        private String cron = "0 0 3 * * *";
    }

    @Data
    public static class SourcesConfig {
        private KudaGoConfig kudago = new KudaGoConfig();
        private TimepadConfig timepad = new TimepadConfig();
        private YandexAfishaConfig yandexAfisha = new YandexAfishaConfig();
        private TbankAfishaConfig tbankAfisha = new TbankAfishaConfig();
        private TickettoshowConfig tickettoshow = new TickettoshowConfig();
        private NovayaOperaConfig novayaOpera = new NovayaOperaConfig();
        private CultureRuConfig cultureRu = new CultureRuConfig();
        private EngineerHistoryConfig engineerHistory = new EngineerHistoryConfig();
        private KassirConfig kassir = new KassirConfig();
    }

    /**
     * Kassir.ru — the biggest regional ticket seller, a host per city. Its site's own JSON API
     * ({@code /page-kit}) needs no token.
     */
    @Data
    public static class KassirConfig {
        private boolean enabled = true;
        private String baseUrl = "https://api.kassir.ru/api";
        /**
         * Category pages to load (leisure only: no kids, courses, cinema sessions or permanent museum
         * exhibitions).
         */
        private List<String> categories = List.of("bilety-na-koncert", "bilety-v-teatr", "bilety-na-shou",
                "bilety-na-standup", "bilety-na-festival", "bilety-na-sportivnye-meropriyatiya",
                "bilety-na-vystavki", "bilety-na-ekskursii");
        private int daysAhead = 60;
        private int pageSize = 200;
        private int maxPages = 5;
        private long requestDelayMs = 500;
    }

    /** «Москва/Питер глазами инженера» — excursion agency; its day afisha pages are parsed. */
    @Data
    public static class EngineerHistoryConfig {
        private boolean enabled = true;
        private List<Site> sites = List.of(
                new Site("https://engineer-history.ru", "Москва"),
                new Site("https://spb.engineer-history.ru", "Санкт-Петербург"));
        private int daysAhead = 21;
        private long requestDelayMs = 500;

        @Data
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class Site {
            private String url;
            private String city;
        }
    }

    /**
     * PRO.Культура.РФ — the official registry of Pushkin card events. The API needs a partner
     * key (partners@team.culture.ru); the provider stays off while the key is empty.
     */
    @Data
    public static class CultureRuConfig {
        private boolean enabled = true;
        private String baseUrl = "https://pro.culture.ru/api/2.5";
        private String apiKey = "";
        private int pageSize = 100;
        private int maxPages = 20;
        private long requestDelayMs = 500;
    }

    @Data
    public static class KudaGoConfig {
        private boolean enabled = true;
        private String baseUrl = "https://kudago.com/public-api/v1.4";
        private int pageSize = 100;
        private int maxPages = 50;
        private long requestDelayMs = 500;
    }

    @Data
    /** Public Timepad afisha (afisha.timepad.ru) — the same JSON API its site uses, no token needed. */
    public static class TimepadConfig {
        private boolean enabled = true;
        private String baseUrl = "https://ontp.timepad.ru/api";
        private String afishaUrl = "https://afisha.timepad.ru";
        /** Organizers taken in full, whatever the category (e.g. 254491 — «Москва в сердце», excursions). */
        private List<Long> organizations = List.of(254491L);
        private int limit = 100;
        private int maxPages = 15;
        private long requestDelayMs = 300;
    }

    @Data
    public static class YandexAfishaConfig {
        private boolean enabled = true;
        private String baseUrl = "https://afisha.yandex.ru";
        /**
         * City pages whose event lists are rendered server-side ("" — the city front page). Rubric lists
         * like /concert and /theatre load in the browser and are empty in HTML.
         */
        private List<String> pages = List.of("", "selections/hot", "selections/weekend", "art", "standup",
                "musical", "excursions");
        private long requestDelayMs = 1000;
    }

    @Data
    public static class TbankAfishaConfig {
        private boolean enabled = true;
        private String baseUrl = "https://www.tbank.ru/afisha";
        private List<String> cities = List.of("moscow", "spb");
        private long requestDelayMs = 1000;
    }

    @Data
    public static class TickettoshowConfig {
        private boolean enabled = true;
        private String baseUrl = "https://api.tickettoshow.ru/api";
        private String refCode = "";
        private String utmQuery = "";
        private long requestDelayMs = 300;
        private String defaultCity = "Москва";
    }

    @Data
    public static class NovayaOperaConfig {
        /**
         * Enabled by default — channel handle confirmed as {@code t.me/novayaopera}
         * (Moscow theatre "Новая Опера им. Е.В. Колобова").
         */
        private boolean enabled = true;
        private String channel = "novayaopera";
        private String promoCode = "GAUDEAMUS";
        private int studentPrice = 600;
        /**
         * Channel posts never quote the full ticket price — they only advertise
         * the 600₽ student price. We surface this number as the strike-through
         * "original" price so the discount is visible. Set to 0 to disable the
         * strike-through.
         */
        private int indicativeFullPrice = 5000;
    }

    @Data
    public static class DefaultsConfig {
        private int futureDays = 90;
    }
}
