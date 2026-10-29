package com.tutorbase.administration.courseware;

import java.util.List;

/**
 * 当前管理员可见的课件范围。
 *
 * <p>一篇课件对本管理员可见，当且仅当（它所属学科已被本管理员认领且该篇未被排除）或
 * （本管理员单独认领了该课件）。可见范围实时由 {@code courseware_catalog}、两张认领表与
 * 排除表 join 得出，不物化：学科认领会自动覆盖该学科此后同步的课件，排除则精确到单篇。
 */
public interface CoursewareScope {

    boolean isVisible(long administratorAccountId, long articleId);

    List<Long> visibleArticleIds(long administratorAccountId);

    /**
     * 返回可嵌入其他查询的 SQL EXISTS 谓词，要求调用方同时绑定
     * {@code :administratorAccountId} 参数。{@code articleIdExpression} 只能是服务端内部
     * 固定的列表达式，不得来自客户端输入。
     */
    String visibleArticlePredicate(String articleIdExpression);
}
