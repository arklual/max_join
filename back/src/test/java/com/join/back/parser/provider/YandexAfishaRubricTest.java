package com.join.back.parser.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YandexAfishaRubricTest {

    @Test
    void mapsUrlRubricToCategoryCode() {
        assertEquals("concert", YandexAfishaParser.rubricFromUrl("https://afisha.yandex.ru/saint-petersburg/concert/shuman-brams"));
        assertEquals("theater", YandexAfishaParser.rubricFromUrl("https://afisha.yandex.ru/moscow/theatre_show/krik-langusty"));
        assertEquals("tour", YandexAfishaParser.rubricFromUrl("https://afisha.yandex.ru/moscow/excursions/legendarnaia-leninka"));
        assertEquals("балет", YandexAfishaParser.rubricFromUrl("https://afisha.yandex.ru/saint-petersburg/ballet/lebedinoe-ozero"));
        assertNull(YandexAfishaParser.rubricFromUrl("https://afisha.yandex.ru/moscow/unknown/x"));
        assertNull(YandexAfishaParser.rubricFromUrl(null));
    }
}
