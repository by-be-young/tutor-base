package com.tutorbase.courseware.storage;

/**
 * 内容哈希的统一格式。
 *
 * <p>新发布的哈希一律是 {@code sha256:<hex>}；历史上由脚本写入的数据可能是裸 hex。
 * 存储层按 hex 落盘，所以读取不受影响；但清单、GC 与同步逻辑在比较哈希时必须先归一化，
 * 否则同一份正文会被误判为「缺失」或「孤儿」。
 */
public final class CoursewareHashes {

    private static final String PREFIX = "sha256:";

    private CoursewareHashes() {
    }

    /** 归一化为 {@code sha256:<hex>}；空白值原样返回，{@code null} 仍是 {@code null}。 */
    public static String normalize(String hash) {
        if (hash == null) {
            return null;
        }
        String trimmed = hash.strip();
        if (trimmed.isEmpty() || trimmed.startsWith(PREFIX)) {
            return trimmed;
        }
        return PREFIX + trimmed;
    }

    /** 忽略前缀与大小写比较两个哈希。 */
    public static boolean same(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return normalize(left).equalsIgnoreCase(normalize(right));
    }
}
