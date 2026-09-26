package com.join.back.parser.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns shouting titles ("ОСОБНЯК МОРОЗОВЫХ В ПОДСОСЕНСКОМ ПЕРЕУЛКЕ") into normal Russian sentence case
 * ("Особняк Морозовых в Подсосенском переулке") without breaking abbreviations (ГУМ, ВДНХ, СПБМРК).
 * <p>
 * Only all-caps Cyrillic words are touched, and only when most of the title is in caps. For each word:
 * <ol>
 *   <li>abbreviations stay as they are — known ones, words without vowels, and short words the
 *       description writes in caps among normal words ("в университете ИТМО");</li>
 *   <li>otherwise the casing is taken from the event description, where organizers write normally —
 *       that keeps names (Морозовых, Хитровка) capitalized;</li>
 *   <li>unknown words: city/country names are capitalized, as are name-like words right after
 *       «особняк/дом/дворец…» or right before «переулок/улица/монастырь…»; the rest is lower case.</li>
 * </ol>
 * The first word and words after «. ! ? « "» start with a capital letter. Latin words are left alone.
 */
public final class TitleCaseNormalizer {

    private static final Pattern WORD = Pattern.compile("[A-Za-zА-Яа-яЁё]+");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-zА-Яа-яЁё]+(?:-[A-Za-zА-Яа-яЁё]+)*");
    private static final Pattern CYRILLIC = Pattern.compile("[А-Яа-яЁё]+");
    private static final String VOWELS = "аеёиоуыэюяАЕЁИОУЫЭЮЯ";
    private static final String SENTENCE_BREAKS = ".!?…\n«\"„“(—-:;";

    private static final Set<String> ABBREVIATIONS = Set.of(
            "гум", "цум", "мгу", "спбгу", "вднх", "ссср", "рф", "мхат", "мхт", "гэс", "зил", "цдх", "гмии",
            "тц", "дк", "ввц", "тасс", "ржд", "нии", "мкад", "цска", "вгик", "гитис", "мфц", "вшэ", "рггу",
            "рудн", "мгимо", "бдт", "мдм", "ао", "оао", "ооо", "ип", "ии", "ит", "сми", "фсб", "кгб", "нквд",
            "мчс", "мвд", "пмэф", "бкз", "гцкз", "кзч");
    private static final Set<String> PROPER_NOUNS = Set.of(
            "москва", "москвы", "москве", "москву", "москвой", "петербург", "петербурга", "петербурге",
            "петербургом", "питер", "питера", "питере", "россия", "россии", "россию", "россией",
            "кремль", "кремля", "кремле", "кремлём", "кремлем", "ленинград", "ленинграда", "ленинграде");
    private static final Set<String> TOPONYM_NOUNS = Set.of(
            "переулок", "переулка", "переулке", "переулком", "улица", "улицы", "улице", "улицу",
            "проспект", "проспекта", "проспекте", "площадь", "площади", "мост", "моста", "мосту",
            "бульвар", "бульвара", "бульваре", "набережная", "набережной", "слобода", "слободы", "слободе",
            "холм", "холма", "холме", "монастырь", "монастыря", "монастыре", "подворье", "подворья",
            "кладбище", "кладбища", "парк", "парка", "парке", "вокзал", "вокзала", "шоссе", "тупик",
            "тупика", "проезд", "проезда", "усадьба", "усадьбы", "усадьбе");
    private static final Set<String> OWNER_NOUNS = Set.of(
            "особняк", "особняка", "особняке", "дом", "дома", "доме", "дворец", "дворца", "дворце",
            "усадьба", "усадьбы", "палаты", "квартира", "квартиры", "музей", "музея", "имени", "им");
    private static final List<String> NAME_ENDINGS = List.of(
            "овых", "евых", "иных", "ского", "цкого", "ской", "цкой", "ских", "цких", "ская", "цкая",
            "ский", "цкий", "ском", "цком", "ова", "ева", "ина", "ына", "ого", "его", "ов", "ев", "ёв",
            "ин", "ын", "ой", "ый", "ий");

    private TitleCaseNormalizer() {
    }

    /** Normalizes {@code title} if it is mostly in caps; {@code context} is the description, may be null. */
    public static String normalize(String title, String context) {
        if (title == null || !isShouting(title)) {
            return title;
        }
        Map<String, Usage> usage = indexContext(context);
        List<MatchResult> tokens = TOKEN.matcher(title).results().toList();
        StringBuilder out = new StringBuilder();
        int last = 0;
        boolean sentenceStart = true;
        for (int t = 0; t < tokens.size(); t++) {
            MatchResult token = tokens.get(t);
            String between = title.substring(last, token.start());
            out.append(between);
            String trimmed = between.stripTrailing();
            if (!trimmed.isEmpty() && ".!?«\"„“".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
                sentenceStart = true;
            }
            String prev = t > 0 ? tokens.get(t - 1).group().toLowerCase(Locale.ROOT) : null;
            String next = t + 1 < tokens.size() ? tokens.get(t + 1).group().toLowerCase(Locale.ROOT) : null;

            String[] parts = token.group().split("-", -1);
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (isCyrillic(part) && isUpper(part)) {
                    String fixed = decide(part, usage, i == 0 ? prev : null, i == parts.length - 1 ? next : null);
                    if (i == 0 && sentenceStart && fixed.equals(fixed.toLowerCase(Locale.ROOT))) {
                        fixed = capitalize(fixed);
                    }
                    parts[i] = fixed;
                }
            }
            out.append(String.join("-", parts));
            sentenceStart = false;
            last = token.end();
        }
        out.append(title.substring(last));
        return out.toString();
    }

    /**
     * At least 60% of the Cyrillic letters are capital — or half of them when the title opens in caps
     * ("ЖИВЫЕ ДРАКОНЫ. Знакомство с…").
     */
    static boolean isShouting(String title) {
        int letters = 0;
        int upper = 0;
        for (char c : title.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC && Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        if (letters < 4) {
            return false;
        }
        if (upper >= letters * 0.6) {
            return true;
        }
        Matcher first = CYRILLIC.matcher(title);
        return upper >= letters * 0.5 && first.find() && first.group().length() > 1 && isUpper(first.group());
    }

    private static String decide(String word, Map<String, Usage> usage, String prev, String next) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (word.length() == 1) {
            return lower;
        }
        if (ABBREVIATIONS.contains(lower) || !hasVowel(word)) {
            return word;
        }
        if (PROPER_NOUNS.contains(lower)) {
            return capitalize(lower);
        }
        String fromContext = byUsage(word, usage.get(lower));
        if (fromContext != null) {
            return fromContext;
        }
        // Another inflection of the same word in the description: "ПОДСОСЕНСКОМ" ~ "Подсосенский".
        if (lower.length() >= 7) {
            String stem = lower.substring(0, lower.length() - 2);
            Usage merged = new Usage();
            usage.forEach((key, u) -> {
                if (key.startsWith(stem)) merged.add(u);
            });
            String fromStem = byUsage(word, merged);
            if (fromStem != null && !fromStem.equals(word)) {
                return fromStem;
            }
        }
        boolean nameLike = NAME_ENDINGS.stream().anyMatch(lower::endsWith);
        if (nameLike && ((next != null && TOPONYM_NOUNS.contains(next) && !TOPONYM_NOUNS.contains(lower))
                || (prev != null && OWNER_NOUNS.contains(prev)))) {
            return capitalize(lower);
        }
        return lower;
    }

    private static String byUsage(String word, Usage u) {
        if (u == null) return null;
        String lower = word.toLowerCase(Locale.ROOT);
        if (u.capitalizedMidSentence > 0 && u.capitalizedMidSentence >= u.lower) return capitalize(lower);
        if (u.lower > 0) return lower;
        if (u.upper > 0 && word.length() <= 5) return word;
        return null;
    }

    private static Map<String, Usage> indexContext(String context) {
        Map<String, Usage> usage = new HashMap<>();
        if (context == null || context.isBlank()) {
            return usage;
        }
        List<MatchResult> words = WORD.matcher(context).results().toList();
        for (int i = 0; i < words.size(); i++) {
            MatchResult m = words.get(i);
            String w = m.group();
            if (!isCyrillic(w)) continue;
            String before = context.substring(0, m.start()).stripTrailing();
            boolean start = before.isEmpty() || SENTENCE_BREAKS.indexOf(before.charAt(before.length() - 1)) >= 0;
            Usage u = usage.computeIfAbsent(w.toLowerCase(Locale.ROOT), k -> new Usage());
            if (w.length() > 1 && isUpper(w)) {
                // Caps among normal words is an abbreviation; a whole shouted phrase is not.
                boolean prevCaps = i > 0 && isShoutedWord(words.get(i - 1).group());
                boolean nextCaps = i + 1 < words.size() && isShoutedWord(words.get(i + 1).group());
                if (!prevCaps && !nextCaps) u.upper++;
            } else if (Character.isUpperCase(w.charAt(0))) {
                if (!start) u.capitalizedMidSentence++;
            } else u.lower++;
        }
        return usage;
    }

    private static final class Usage {
        int lower;
        int capitalizedMidSentence;
        int upper;

        void add(Usage other) {
            lower += other.lower;
            capitalizedMidSentence += other.capitalizedMidSentence;
            upper += other.upper;
        }
    }

    private static boolean isShoutedWord(String s) {
        return s.length() > 1 && isUpper(s);
    }

    private static boolean isCyrillic(String s) {
        return CYRILLIC.matcher(s).matches();
    }

    private static boolean isUpper(String s) {
        return s.equals(s.toUpperCase(Locale.ROOT)) && !s.equals(s.toLowerCase(Locale.ROOT));
    }

    private static boolean hasVowel(String s) {
        for (char c : s.toCharArray()) {
            if (VOWELS.indexOf(c) >= 0) return true;
        }
        return false;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
