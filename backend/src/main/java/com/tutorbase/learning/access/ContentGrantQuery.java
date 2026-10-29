package com.tutorbase.learning.access;

import java.util.List;
import java.util.Optional;

public interface ContentGrantQuery {

    Optional<List<Long>> findArticleIds(long learnerId);

    /** 特殊测试学员拥有全部课件权限；即使课件目录为空，前端也应按全量文章放行。 */
    Optional<Boolean> isSpecialTestLearner(long learnerId);
}
