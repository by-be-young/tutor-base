package com.tutorbase.courseware;

import java.util.List;

/** 课件托管领域的失败类型；HTTP 映射见 {@code web.CoursewareHttpExceptionHandler}。 */
public final class CoursewareFailures {

    private CoursewareFailures() {
    }

    /** 发布校验失败：逐条说明原因。 */
    public static final class Validation extends RuntimeException {
        private final List<String> errors;

        public Validation(List<String> errors) {
            super("课件发布校验失败");
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }

    /** 老师或课件不存在。 */
    public static final class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** 未授权：未关联该老师，或未被授权该课件。 */
    public static final class Forbidden extends RuntimeException {
        public Forbidden(String message) {
            super(message);
        }
    }

    /** 来源未开放获取：未分享该科目/该篇，或 allow_copy=false。 */
    public static final class SourceForbidden extends RuntimeException {
        public SourceForbidden(String message) {
            super(message);
        }
    }

    /** 目标路径已存在：获取/导入时本地同名篇目冲突。 */
    public static final class TargetExists extends RuntimeException {
        public TargetExists(String message) {
            super(message);
        }
    }

    /** 发布版本冲突：expectVersion 与当前指针不一致。 */
    public static final class VersionConflict extends RuntimeException {
        public VersionConflict(String message) {
            super(message);
        }
    }
}
