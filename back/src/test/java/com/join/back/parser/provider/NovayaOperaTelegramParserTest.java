package com.join.back.parser.provider;

import com.join.back.model.entity.EventSource;
import com.join.back.parser.config.ParserProperties;
import com.join.back.parser.dto.RawExternalEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovayaOperaTelegramParserTest {

    // Shows two months ahead: the parser drops recently-past dates, so fixed dates rot.
    private static final Month MONTH = LocalDate.now().plusMonths(2).getMonth();
    private static final String MONTH_RU = new String[]{"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"}[MONTH.ordinal()];

    private static final String SAMPLE_POST_TEXT = "" +
            "🎓 В Новой Опере – акция для студентов!\n" +
            "\n" +
            "По промокоду GAUDEAMUS можно приобрести билеты по специальной фиксированной цене 600 рублей!\n" +
            "\n" +
            "Промокод действует на спектакли:\n" +
            "\n" +
            "22 и 23 " + MONTH_RU + " – «Лючия ди Ламмермур (https://novayaopera.ru/plays/opera/lyuchiya-di-lammermur/)» Г. Доницетти\n" +
            "\n" +
            "27 " + MONTH_RU + " – «Волшебная флейта (https://novayaopera.ru/plays/opera/volshebnaya-fleyta/)» В.А. Моцарта, опера в концертном исполнении\n" +
            "\n" +
            "Обратите внимание:\n" +
            "По промокоду GAUDEAMUS в одном заказе можно оформить только один билет.";

    private NovayaOperaTelegramParser newParser() {
        ParserProperties props = new ParserProperties();
        props.getSources().getNovayaOpera().setEnabled(true);
        props.getSources().getNovayaOpera().setChannel("novayaopera");
        props.getSources().getNovayaOpera().setPromoCode("GAUDEAMUS");
        props.getSources().getNovayaOpera().setStudentPrice(600);
        props.getSources().getNovayaOpera().setIndicativeFullPrice(5000);
        return new NovayaOperaTelegramParser(props);
    }

    @Test
    void parseDatesExpandsMultiDayAndMixedSeparators() {
        NovayaOperaTelegramParser parser = newParser();
        int year = nextOccurrenceYear(MONTH, 22);

        List<LocalDate> result = parser.parseDates("22 и 23 " + MONTH_RU);
        assertEquals(List.of(LocalDate.of(year, MONTH, 22), LocalDate.of(year, MONTH, 23)), result);

        List<LocalDate> single = parser.parseDates("27 " + MONTH_RU);
        assertEquals(List.of(LocalDate.of(year, MONTH, 27)), single);

        List<LocalDate> range = parser.parseDates("22-23 " + MONTH_RU);
        assertEquals(List.of(LocalDate.of(year, MONTH, 22), LocalDate.of(year, MONTH, 23)), range);
    }

    @Test
    void parsePostFansOutMultiDateProductionLines() throws Exception {
        NovayaOperaTelegramParser parser = newParser();
        ParserProperties props = new ParserProperties();
        props.getSources().getNovayaOpera().setPromoCode("GAUDEAMUS");
        props.getSources().getNovayaOpera().setStudentPrice(600);
        props.getSources().getNovayaOpera().setIndicativeFullPrice(5000);
        props.getSources().getNovayaOpera().setChannel("novayaopera");

        Element postEl = synthesizePost(SAMPLE_POST_TEXT, "/novayaopera/5244");

        Method parsePost = NovayaOperaTelegramParser.class.getDeclaredMethod(
                "parsePost", Element.class, ParserProperties.NovayaOperaConfig.class);
        parsePost.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RawExternalEvent> events = (List<RawExternalEvent>) parsePost.invoke(
                parser, postEl, props.getSources().getNovayaOpera());

        assertEquals(3, events.size(), "two productions × (2 + 1) dates = 3 events");

        int year = nextOccurrenceYear(MONTH, 22);

        RawExternalEvent lucia22 = events.get(0);
        assertEquals(EventSource.TELEGRAM, lucia22.getSource());
        assertEquals(LocalDate.of(year, MONTH, 22), lucia22.getEventDate());
        assertTrue(lucia22.getTitle().contains("Лючия ди Ламмермур"), lucia22.getTitle());
        assertEquals("GAUDEAMUS", lucia22.getStudentPromoCode());
        assertEquals(BigDecimal.valueOf(600), lucia22.getMinPrice());
        assertEquals(BigDecimal.valueOf(5000), lucia22.getOriginalPrice());
        assertNotNull(lucia22.getTicketUrl());
        assertTrue(lucia22.getTicketUrl().contains("lyuchiya-di-lammermur"), lucia22.getTicketUrl());
        assertFalse(lucia22.getTitle().contains("("), "title must not include the embedded URL");
        assertTrue(lucia22.getExternalId().endsWith("#" + LocalDate.of(year, MONTH, 22)));

        RawExternalEvent flute = events.get(2);
        assertEquals(LocalDate.of(year, MONTH, 27), flute.getEventDate());
        assertTrue(flute.getTitle().contains("Волшебная флейта"), flute.getTitle());
        assertTrue(flute.getTicketUrl().contains("volshebnaya-fleyta"));
    }

    @Test
    void parsePostIgnoresPostsWithoutGaudeamus() throws Exception {
        NovayaOperaTelegramParser parser = newParser();
        Element postEl = synthesizePost("Обычная новость без промокода. 27 " + MONTH_RU + " - «Волшебная флейта» Моцарта.",
                "/novayaopera/9999");
        Method parsePost = NovayaOperaTelegramParser.class.getDeclaredMethod(
                "parsePost", Element.class, ParserProperties.NovayaOperaConfig.class);
        parsePost.setAccessible(true);
        ParserProperties props = new ParserProperties();
        @SuppressWarnings("unchecked")
        List<RawExternalEvent> events = (List<RawExternalEvent>) parsePost.invoke(
                parser, postEl, props.getSources().getNovayaOpera());
        assertTrue(events.isEmpty(), "no Gaudeamus mention -> no events");
    }

    @Test
    void gaudeamusRegexMatchesCommonMisspellings() {
        NovayaOperaTelegramParser parser = newParser();
        // The whole-post test above already covers GAUDEAMUS; here check the
        // historical spellings we also accept (Gaudiamus / GAUDIMUS).
        for (String spelling : List.of(
                "По промокоду Gaudeamus можно",
                "По промокоду GAUDEAMUS можно",
                "По промокоду Gaudiamus можно",
                "По промокоду gaudimus можно")) {
            Element postEl = synthesizePost(spelling + "\n22 " + MONTH_RU + " – «Тестовый спектакль» Тест",
                    "/novayaopera/" + Math.abs(spelling.hashCode()));
            try {
                Method m = NovayaOperaTelegramParser.class.getDeclaredMethod(
                        "parsePost", Element.class, ParserProperties.NovayaOperaConfig.class);
                m.setAccessible(true);
                ParserProperties props = new ParserProperties();
                @SuppressWarnings("unchecked")
                List<RawExternalEvent> events = (List<RawExternalEvent>) m.invoke(
                        parser, postEl, props.getSources().getNovayaOpera());
                assertFalse(events.isEmpty(), "expected match for spelling: " + spelling);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Element synthesizePost(String text, String permalinkPath) {
        // Inject literal \n as <br> so the parser's br→\n normalisation gives us back our newlines.
        String html = text.replace("\n", "<br/>");
        Document doc = Jsoup.parse(
                "<div class=\"tgme_widget_message_wrap\">" +
                        "<a class=\"tgme_widget_message_date\" href=\"https://t.me" + permalinkPath + "\"></a>" +
                        "<div class=\"tgme_widget_message_text\">" + html + "</div>" +
                        "</div>");
        return doc.selectFirst(".tgme_widget_message_wrap");
    }

    private static int nextOccurrenceYear(Month month, int day) {
        LocalDate candidate = LocalDate.of(LocalDate.now().getYear(), month, day);
        return candidate.isBefore(LocalDate.now().minusDays(1))
                ? candidate.getYear() + 1
                : candidate.getYear();
    }
}
