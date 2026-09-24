package com.join.back.util;

/**
 * Escaping for messenger "HTML" message formatting (MAX, Telegram). Those parsers
 * understand only &amp;amp; &amp;lt; &amp;gt; &amp;quot; — named entities such as
 * &amp;laquo; would show up literally, so Unicode (« » …) must stay as is.
 */
public final class MessengerHtml {

    private MessengerHtml() {
    }

    public static String escape(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
