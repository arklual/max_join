package com.join.back.parser.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleCaseNormalizerTest {

    private static String fix(String title) {
        return TitleCaseNormalizer.normalize(title, null);
    }

    @Test
    void takesNamesCasingFromDescription() {
        assertEquals("Особняк Морозовых в Подсосенском переулке", TitleCaseNormalizer.normalize(
                "ОСОБНЯК МОРОЗОВЫХ В ПОДСОСЕНСКОМ ПЕРЕУЛКЕ",
                "Уникальный и очень красивый особняк Морозовых. Уже началась реставрация."));
        assertEquals("Детская экскурсия. Детективная Хитровка", TitleCaseNormalizer.normalize(
                "ДЕТСКАЯ ЭКСКУРСИЯ. ДЕТЕКТИВНАЯ ХИТРОВКА",
                "Отправляемся расследовать загадочное преступление на Хитровке!"));
    }

    @Test
    void keepsAbbreviations() {
        assertEquals("ГУМ как зеркало страны", fix("ГУМ КАК ЗЕРКАЛО СТРАНЫ"));
        assertEquals("Лекция о ВДНХ и МГУ", fix("ЛЕКЦИЯ О ВДНХ И МГУ"));
        assertEquals("17 октября Единый день открытых дверей рыбопромышленной отрасли «Флагман кадров: СПБМРК для индустрии будущего»",
                fix("17 октября Единый ДЕНЬ ОТКРЫТЫХ ДВЕРЕЙ рыбопромышленной отрасли «ФЛАГМАН КАДРОВ: СПБМРК ДЛЯ ИНДУСТРИИ БУДУЩЕГО»"));
        assertEquals("Детская опера-мюзикл XXI века (Сергей Плешак)", fix("ДЕТСКАЯ ОПЕРА-МЮЗИКЛ XXI ВЕКА (Сергей Плешак)"));
    }

    @Test
    void tellsAbbreviationsFromShoutedPhrasesInDescription() {
        assertEquals("Шоу \"Женщина\"", TitleCaseNormalizer.normalize("ШОУ \"ЖЕНЩИНА\"",
                "ШОУ \"ЖЕНЩИНА\" — от рождения до раскаяния"));
        assertEquals("Живые драконы. Знакомство с ТЕГУ 7+", TitleCaseNormalizer.normalize(
                "ЖИВЫЕ ДРАКОНЫ. Знакомство с ТЕГУ 7+",
                "Живое занятие с первым в России заводчиком Аргентинских и Колумбийских ТЕГУ"));
    }

    @Test
    void capitalizesNamesByPositionWithoutDescription() {
        assertEquals("Дом Пашкова (16+)", fix("ДОМ ПАШКОВА (16+)"));
        assertEquals("Особняк Тарасова. Итальянское палаццо с Еленой Дворянцевой",
                fix("ОСОБНЯК ТАРАСОВА. ИТАЛЬЯНСКОЕ ПАЛАЦЦО с Еленой Дворянцевой"));
        assertEquals("От Крутицкого подворья в Новоспасский монастырь", fix("ОТ КРУТИЦКОГО ПОДВОРЬЯ В НОВОСПАССКИЙ МОНАСТЫРЬ"));
        assertEquals("Иностранцы в Москве", fix("ИНОСТРАНЦЫ В МОСКВЕ"));
        assertEquals("Приз петербургских композиторов", fix("ПРИЗ ПЕТЕРБУРГСКИХ КОМПОЗИТОРОВ"));
    }

    @Test
    void capitalizesAfterOpeningQuoteAndHandlesHyphens() {
        assertEquals("Квиз \"Культура и искусство\"", fix("КВИЗ \"КУЛЬТУРА И ИСКУССТВО\""));
        assertEquals("Сандуны царь-бани", fix("САНДУНЫ ЦАРЬ-БАНИ"));
        assertEquals("Кинобранч «Амели» | 04.10 | 14:00", fix("КИНОБРАНЧ «Амели» | 04.10 | 14:00"));
    }

    @Test
    void leavesNormalAndMostlyLowercaseTitlesAlone() {
        assertEquals("Высотка на Котельнической набережной", fix("Высотка на Котельнической набережной"));
        assertEquals("фестиваль «МУЗ БИТ АНЛОК» / MUZ BIT UNLOCK", fix("фестиваль «МУЗ БИТ АНЛОК» / MUZ BIT UNLOCK"));
        assertEquals("Концерт группы ДДТ", fix("Концерт группы ДДТ"));
    }
}
