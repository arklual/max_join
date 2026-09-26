package com.join.back.parser.provider;

import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Москва глазами инженера» / «Петербург глазами инженера» (engineer-history.ru): excursions and
 * architecture lectures. The site has a server-rendered afisha per day — {@code /events/2026-09-27} —
 * with a card per session: time, price, tags and a buy link carrying {@code startDate} and {@code event_id}.
 * A day without sessions shows the next day that has them, so the date is taken from the link.
 * Sold-out sessions and family (kids) tours are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineerHistoryParser implements EventProvider {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern EVENT_ID = Pattern.compile("[?&]event_id=(\\d+)");
    private static final Pattern START_DATE = Pattern.compile("[?&]startDate=(\\d{4}-\\d{2}-\\d{2})");

    private final ParserProperties parserProperties;

    @Override
    public EventSource getSource() {
        return EventSource.ENGINEER_HISTORY;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.EngineerHistoryConfig config = parserProperties.getSources().getEngineerHistory();
        if (!config.isEnabled()) {
            log.info("Engineer-history parser is disabled, skipping");
            return List.of();
        }

        RestClient restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9")
                .build();
        LocalDate today = LocalDate.now(ZONE);
        List<RawExternalEvent> result = new ArrayList<>();

        for (ParserProperties.EngineerHistoryConfig.Site site : config.getSites()) {
            int before = result.size();
            for (int day = 0; day < config.getDaysAhead(); day++) {
                LocalDate date = today.plusDays(day);
                try {
                    String html = restClient.get()
                            .uri(site.getUrl() + "/events/" + date)
                            .retrieve()
                            .body(String.class);
                    result.addAll(parseDay(html, site.getUrl(), site.getCity(), date));
                } catch (RestClientException e) {
                    log.warn("Engineer-history request failed for {} {}: {}", site.getCity(), date, e.getMessage());
                }
                if (!sleep(config.getRequestDelayMs())) {
                    return result;
                }
            }
            log.info("Engineer-history: {} sessions for {}", result.size() - before, site.getCity());
        }
        return result;
    }

    /** Parses one day page; {@code date} is only a fallback for cards whose link has no startDate. */
    List<RawExternalEvent> parseDay(String html, String baseUrl, String city, LocalDate date) {
        List<RawExternalEvent> out = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return out;
        }
        Document doc = Jsoup.parse(html, baseUrl);
        for (Element card : doc.select("div.card.lubacard")) {
            try {
                RawExternalEvent raw = parseCard(card, city, date);
                if (raw != null) {
                    out.add(raw);
                }
            } catch (Exception e) {
                log.debug("Engineer-history: skipping card: {}", e.getMessage());
            }
        }
        return out;
    }

    private RawExternalEvent parseCard(Element card, String city, LocalDate date) {
        Element link = card.selectFirst("a.image-card__btn");
        Element titleEl = card.selectFirst(".lubacard-title");
        Element priceEl = card.selectFirst(".lubacard-price");
        if (link == null || titleEl == null || priceEl == null) {
            return null;
        }
        // Sold out ("Билетов нет · Уведомить меня") and family tours are not for finding a companion.
        String priceText = priceEl.text();
        if (priceText.toLowerCase().contains("билетов нет") || !card.select(".image-card__badge--children").isEmpty()) {
            return null;
        }
        String digits = priceText.replaceAll("[^0-9]", "");
        BigDecimal price = digits.isEmpty() ? null : new BigDecimal(digits);

        String title = titleEl.text().trim();
        String ticketUrl = link.absUrl("href");
        if (title.isEmpty() || ticketUrl.isEmpty()) {
            return null;
        }

        LocalTime time = null;
        Element dateEl = card.selectFirst(".lubacard-date");
        if (dateEl != null) {
            Matcher m = TIME.matcher(dateEl.text());
            if (m.find()) {
                time = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            }
        }

        Matcher dateMatcher = START_DATE.matcher(ticketUrl);
        if (dateMatcher.find()) {
            date = LocalDate.parse(dateMatcher.group(1));
        }

        Matcher idMatcher = EVENT_ID.matcher(ticketUrl);
        String externalId = idMatcher.find()
                ? idMatcher.group(1)
                : UriComponentsBuilder.fromUriString(ticketUrl).build().getPath() + "#" + date + "#" + time;

        List<String> tags = new ArrayList<>();
        for (Element tag : card.select(".lubacard-tag")) {
            String text = tag.text().replace(' ', ' ').trim();
            if (!text.isEmpty()) {
                tags.add(text);
            }
        }

        Element img = card.selectFirst("img.lubacard-img");
        return RawExternalEvent.builder()
                .externalId(externalId)
                .source(EventSource.ENGINEER_HISTORY)
                .title(title.length() > 255 ? title.substring(0, 255) : title)
                .description(tags.isEmpty() ? null : String.join(" · ", tags))
                .rawCategories(tags)
                .imageUrl(img != null && !img.absUrl("src").isEmpty() ? img.absUrl("src") : null)
                .minPrice(price)
                .eventDate(date)
                .eventTime(time)
                .ticketUrl(ticketUrl)
                .city(city)
                .build();
    }

    private static boolean sleep(long ms) {
        if (ms <= 0) return true;
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
