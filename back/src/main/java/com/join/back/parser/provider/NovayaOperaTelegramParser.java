package com.join.back.parser.provider;

import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the Novaya Opera official Telegram channel.
 *
 * <p>Scrapes the public preview at {@code https://t.me/s/<channel>} and looks for
 * posts that announce the {@code GAUDEAMUS} student promo (any ticket for 600₽).
 *
 * <p>The channel uses a stable post shape:
 * <pre>
 *   🎓 В Новой Опере – акция для студентов!
 *   По промокоду GAUDEAMUS можно приобрести билеты ... 600 рублей!
 *   Промокод действует на спектакли:
 *
 *   22 и 23 мая – «Лючия ди Ламмермур (https://novayaopera.ru/plays/opera/...)» Г. Доницетти
 *   27 мая – «Волшебная флейта (...)» В.А. Моцарта, опера в концертном исполнении
 *
 *   ...
 * </pre>
 * Each production line yields one event per date listed. Multi-date lines
 * like {@code "22 и 23 мая"} or {@code "22-23 мая"} fan out into separate events.
 *
 * <p>Posts that don't mention {@code Gaudeamus} are ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NovayaOperaTelegramParser implements EventProvider {

    private static final Pattern GAUDEAMUS_PATTERN = Pattern.compile(
            "gaud[ei][aei]?mus", Pattern.CASE_INSENSITIVE);

    // Production line: <date_part> <separator> «<title>» <tail>
    // Separator is an em/en/hyphen dash. Title is in « » Russian quotes.
    private static final Pattern PRODUCTION_LINE = Pattern.compile(
            "^([0-9].{0,40}?(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря))" +
                    "\\s*[\\-–—]\\s*«([^»]+)»\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    // Single day or day-range inside a date part: "22", "22-23", "22 и 23"
    private static final Pattern DAY_TOKEN = Pattern.compile(
            "(\\d{1,2})(?:\\s*[-–—]\\s*(\\d{1,2})|\\s+и\\s+(\\d{1,2}))?", Pattern.CASE_INSENSITIVE);

    private static final Pattern MONTH_TOKEN = Pattern.compile(
            "(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)",
            Pattern.CASE_INSENSITIVE);

    // URL inside the «...» (the ticket link Novaya Opera embeds right after the title).
    private static final Pattern URL_IN_TITLE = Pattern.compile(
            "\\((https?://[^)\\s]+)\\)");

    private static final Map<String, Month> RU_MONTHS = new HashMap<>();
    static {
        RU_MONTHS.put("января", Month.JANUARY);
        RU_MONTHS.put("февраля", Month.FEBRUARY);
        RU_MONTHS.put("марта", Month.MARCH);
        RU_MONTHS.put("апреля", Month.APRIL);
        RU_MONTHS.put("мая", Month.MAY);
        RU_MONTHS.put("июня", Month.JUNE);
        RU_MONTHS.put("июля", Month.JULY);
        RU_MONTHS.put("августа", Month.AUGUST);
        RU_MONTHS.put("сентября", Month.SEPTEMBER);
        RU_MONTHS.put("октября", Month.OCTOBER);
        RU_MONTHS.put("ноября", Month.NOVEMBER);
        RU_MONTHS.put("декабря", Month.DECEMBER);
    }

    private final ParserProperties parserProperties;

    @Override
    public EventSource getSource() {
        return EventSource.TELEGRAM;
    }

    @Override
    public List<RawExternalEvent> fetchEvents() {
        ParserProperties.NovayaOperaConfig config = parserProperties.getSources().getNovayaOpera();
        if (!config.isEnabled()) {
            log.info("Novaya Opera TG parser is disabled, skipping");
            return List.of();
        }
        String channel = config.getChannel();
        if (channel == null || channel.isBlank()) {
            log.warn("Novaya Opera TG parser: channel handle not configured");
            return List.of();
        }

        String url = "https://t.me/s/" + channel;
        String html;
        try {
            RestClient client = RestClient.builder().baseUrl(url).build();
            html = client.get()
                    .header("User-Agent", "Mozilla/5.0 (compatible; JoinBot/1.0)")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to fetch Novaya Opera TG channel '{}': {}", channel, e.getMessage());
            return List.of();
        }
        if (html == null || html.isBlank()) {
            log.warn("Novaya Opera TG channel returned empty body");
            return List.of();
        }

        Document doc = Jsoup.parse(html);
        List<Element> posts = doc.select(".tgme_widget_message_wrap");
        log.info("Novaya Opera TG: scanned {} posts from {}", posts.size(), channel);

        List<RawExternalEvent> all = new ArrayList<>();
        for (Element post : posts) {
            try {
                all.addAll(parsePost(post, config));
            } catch (Exception e) {
                log.warn("Failed to parse Novaya Opera post: {}", e.getMessage());
            }
        }

        // Multiple posts may announce or remind about the same show on the same
        // date (e.g. a launch post and a "tonight!" reminder). Collapse them so
        // we emit one event per (production, date).
        java.util.LinkedHashMap<String, RawExternalEvent> deduped = new java.util.LinkedHashMap<>();
        for (RawExternalEvent ev : all) {
            String key = (ev.getTicketUrl() != null && ev.getTicketUrl().contains("novayaopera.ru")
                    ? ev.getTicketUrl()
                    : ev.getTitle()) + "@" + ev.getEventDate();
            deduped.putIfAbsent(key, rebrandExternalId(ev));
        }
        List<RawExternalEvent> out = new ArrayList<>(deduped.values());
        log.info("Novaya Opera TG: extracted {} Gaudeamus events ({} after dedup)", all.size(), out.size());
        return out;
    }

    /**
     * Replaces the per-post permalink-based externalId with one derived from
     * (ticketUrl, date), so re-runs of the parser deduplicate against existing
     * DB rows even if the source post changes.
     */
    private RawExternalEvent rebrandExternalId(RawExternalEvent ev) {
        String anchor = (ev.getTicketUrl() != null && ev.getTicketUrl().contains("novayaopera.ru")
                ? ev.getTicketUrl()
                : ev.getTitle());
        ev.setExternalId("novaya-opera|" + anchor + "|" + ev.getEventDate());
        return ev;
    }

    private List<RawExternalEvent> parsePost(Element post, ParserProperties.NovayaOperaConfig config) {
        Element textNode = post.selectFirst(".tgme_widget_message_text");
        if (textNode == null) {
            return List.of();
        }
        String text = extractPostText(textNode);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (!GAUDEAMUS_PATTERN.matcher(text).find()) {
            return List.of();
        }

        // Post-level metadata.
        String postPermalink = null;
        Element link = post.selectFirst("a.tgme_widget_message_date");
        if (link != null) {
            postPermalink = link.attr("href");
        }
        String imageUrl = extractImageUrl(post);

        String note = String.format(
                "Промокод «%s» — для студентов любой билет стоит %d ₽ " +
                        "(в одном заказе можно оформить только один льготный билет, " +
                        "вход в театр строго по студенческому).",
                config.getPromoCode(), config.getStudentPrice());

        BigDecimal indicativeOriginal = config.getIndicativeFullPrice() > 0
                ? BigDecimal.valueOf(config.getIndicativeFullPrice())
                : null;

        List<RawExternalEvent> events = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher m = PRODUCTION_LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String datePart = m.group(1).trim();
            String titleWithMaybeUrl = m.group(2).trim();
            String tail = m.group(3).trim();

            // Pull out the ticket URL hiding inside the «...» bracket if present.
            String ticketUrl = null;
            String cleanTitle = titleWithMaybeUrl;
            Matcher u = URL_IN_TITLE.matcher(titleWithMaybeUrl);
            if (u.find()) {
                ticketUrl = u.group(1);
                cleanTitle = (titleWithMaybeUrl.substring(0, u.start())
                        + titleWithMaybeUrl.substring(u.end())).trim();
            }
            cleanTitle = cleanTitle.replaceAll("\\s+", " ").trim();
            if (cleanTitle.isEmpty()) {
                continue;
            }

            List<LocalDate> dates = parseDates(datePart);
            if (dates.isEmpty()) {
                continue;
            }

            for (LocalDate date : dates) {
                String externalId = (postPermalink != null && !postPermalink.isBlank()
                        ? postPermalink
                        : "novaya-opera-" + Integer.toHexString(cleanTitle.hashCode()))
                        + "#" + date;

                String description = "🎓 Студенческая акция Новой Оперы.\n\n" +
                        cleanTitle + (tail.isEmpty() ? "" : " — " + tail) + "\n\n" +
                        note;

                String composedTitle = "Новая Опера — " + cleanTitle;

                events.add(RawExternalEvent.builder()
                        .externalId(externalId)
                        .source(EventSource.TELEGRAM)
                        .title(truncate(composedTitle, 255))
                        .description(description)
                        .rawCategories(List.of("Опера", "Театр"))
                        .minPrice(BigDecimal.valueOf(config.getStudentPrice()))
                        .originalPrice(indicativeOriginal)
                        .studentPromoCode(config.getPromoCode())
                        .studentPromoNote(note)
                        .eventDate(date)
                        .imageUrl(imageUrl)
                        .ticketUrl(ticketUrl != null ? ticketUrl : "https://t.me/" + config.getChannel())
                        .city("Москва")
                        .build());
            }
        }
        if (events.isEmpty()) {
            log.debug("Novaya Opera TG: post mentions GAUDEAMUS but no production lines parsed; permalink={}",
                    postPermalink);
        }
        return events;
    }

    /**
     * Parses a date phrase like {@code "22 и 23 мая"} / {@code "22-23 мая"} /
     * {@code "27 мая"} into the list of concrete LocalDate values it covers.
     */
    List<LocalDate> parseDates(String datePart) {
        String lower = datePart.toLowerCase(Locale.ROOT);
        Matcher monthMatcher = MONTH_TOKEN.matcher(lower);
        if (!monthMatcher.find()) {
            return List.of();
        }
        Month month = RU_MONTHS.get(monthMatcher.group(1));
        if (month == null) {
            return List.of();
        }
        // Restrict to the days part before the month name.
        String daysPart = lower.substring(0, monthMatcher.start()).trim();

        List<Integer> days = new ArrayList<>();
        Matcher dm = DAY_TOKEN.matcher(daysPart);
        while (dm.find()) {
            int first = Integer.parseInt(dm.group(1));
            if (!days.contains(first)) {
                days.add(first);
            }
            // Day range: "22-23"
            if (dm.group(2) != null) {
                int last = Integer.parseInt(dm.group(2));
                for (int d = first + 1; d <= last; d++) {
                    if (!days.contains(d)) {
                        days.add(d);
                    }
                }
            }
            // "22 и 23"
            if (dm.group(3) != null) {
                int other = Integer.parseInt(dm.group(3));
                if (!days.contains(other)) {
                    days.add(other);
                }
            }
        }
        if (days.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        // Channel keeps recently-finished posts in the preview. Don't roll a
        // few-days-past date forward by a year — it's almost always a stale
        // announcement of the show that just happened. We *do* roll forward
        // when the gap is large (>180 days), since at that point the post is
        // a calendar leftover from the previous year and the production is
        // repeating on the same date.
        List<LocalDate> out = new ArrayList<>();
        for (int day : days) {
            try {
                LocalDate candidate = LocalDate.of(today.getYear(), month, day);
                if (candidate.isBefore(today.minusDays(1))) {
                    long daysPast = today.toEpochDay() - candidate.toEpochDay();
                    if (daysPast > 180) {
                        candidate = LocalDate.of(today.getYear() + 1, month, day);
                    } else {
                        // Recent past — skip, this is a stale post.
                        continue;
                    }
                }
                out.add(candidate);
            } catch (Exception ignored) {
                // skip impossible (day, month) combination
            }
        }
        return out;
    }

    /**
     * Reconstructs the post text the way a human would read it. In particular:
     * <ul>
     *   <li>{@code <br>} becomes a newline so production lines stay one-per-line;</li>
     *   <li>{@code <a href="…">title</a>} becomes {@code "title (href)"} so we can
     *       recover the ticket URL from inside the «...» title brackets.</li>
     * </ul>
     */
    String extractPostText(Element textNode) {
        StringBuilder sb = new StringBuilder();
        textNode.traverse(new NodeVisitor() {
            private int anchorDepth = 0;
            private int anchorStart = -1;
            private String anchorHref = null;

            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode tn) {
                    if (anchorDepth == 0) {
                        sb.append(tn.getWholeText());
                    } else {
                        // We're inside an <a>; keep collecting the inner text only.
                        sb.append(tn.getWholeText());
                    }
                } else if (node instanceof Element el) {
                    String tag = el.tagName().toLowerCase(Locale.ROOT);
                    if ("br".equals(tag)) {
                        sb.append('\n');
                    } else if ("a".equals(tag)) {
                        if (anchorDepth == 0) {
                            anchorStart = sb.length();
                            anchorHref = el.attr("href");
                        }
                        anchorDepth++;
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element el && "a".equalsIgnoreCase(el.tagName())) {
                    anchorDepth--;
                    if (anchorDepth == 0 && anchorHref != null && !anchorHref.isBlank()
                            && sb.length() > anchorStart) {
                        // Only inline non-junk URLs (skip mailto: / tg: protocols etc).
                        if (anchorHref.startsWith("http")) {
                            sb.append(" (").append(anchorHref).append(")");
                        }
                        anchorHref = null;
                        anchorStart = -1;
                    }
                }
            }
        });
        return sb.toString();
    }

    private String extractImageUrl(Element post) {
        Element photo = post.selectFirst("a.tgme_widget_message_photo_wrap");
        if (photo == null) {
            return null;
        }
        String style = photo.attr("style");
        Matcher m = Pattern.compile("background-image:url\\('([^']+)'\\)").matcher(style);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String truncate(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }
}
