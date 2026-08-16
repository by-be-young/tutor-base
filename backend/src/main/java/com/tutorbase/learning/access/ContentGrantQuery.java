package com.tutorbase.learning.access;

import java.util.List;
import java.util.Optional;

public interface ContentGrantQuery {

    Optional<List<Long>> findArticleIds(long learnerId);
}
