package com.tutorbase.administration.learner;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.OptionalLong;

final class LearnerCursor {

    private static final String VERSION_PREFIX = "v1:";

    private LearnerCursor() {
    }

    static OptionalLong decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith(VERSION_PREFIX)) {
                throw new IllegalArgumentException("Unsupported cursor version");
            }
            long id = Long.parseLong(decoded.substring(VERSION_PREFIX.length()));
            if (id < 0 || !encode(id).equals(cursor)) {
                throw new IllegalArgumentException("Invalid cursor payload");
            }
            return OptionalLong.of(id);
        } catch (IllegalArgumentException exception) {
            throw new InvalidLearnerPageRequest(
                    "cursor", "malformed_request", "The cursor is invalid.", exception);
        }
    }

    static String encode(long id) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((VERSION_PREFIX + id).getBytes(StandardCharsets.UTF_8));
    }
}
