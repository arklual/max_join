package com.join.back.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessengerHtmlTest {

    @Test
    void keepsUnicodePunctuationAndEscapesOnlyMarkup() {
        assertEquals("Идёшь на «Аленький цветочек»? Зацепит…",
                MessengerHtml.escape("Идёшь на «Аленький цветочек»? Зацепит…"));
        assertEquals("a &lt;b&gt; &amp; &quot;c&quot;", MessengerHtml.escape("a <b> & \"c\""));
        assertEquals("", MessengerHtml.escape(null));
    }
}
