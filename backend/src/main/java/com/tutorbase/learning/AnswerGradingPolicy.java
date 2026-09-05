package com.tutorbase.learning;

final class AnswerGradingPolicy {

    private AnswerGradingPolicy() {
    }

    static String grade(String submittedAnswer, String referenceAnswer) {
        return normalize(submittedAnswer).equals(normalize(referenceAnswer)) ? "correct" : "wrong";
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
