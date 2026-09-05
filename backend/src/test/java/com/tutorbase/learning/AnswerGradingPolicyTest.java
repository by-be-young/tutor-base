package com.tutorbase.learning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerGradingPolicyTest {

    @Test
    void acceptsEquivalentWindowsAndUnixLineEndings() {
        assertThat(AnswerGradingPolicy.grade("first\r\nsecond", "first\nsecond")).isEqualTo("correct");
        assertThat(AnswerGradingPolicy.grade("first\rsecond", "first\nsecond")).isEqualTo("correct");
    }

    @Test
    void keepsAnswerContentAndWhitespaceSignificant() {
        assertThat(AnswerGradingPolicy.grade("42", "42 ")).isEqualTo("wrong");
        assertThat(AnswerGradingPolicy.grade("42", "41")).isEqualTo("wrong");
    }
}
