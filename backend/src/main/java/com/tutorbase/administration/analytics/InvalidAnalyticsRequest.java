package com.tutorbase.administration.analytics;

/** 管理员统计接口的查询参数不可解析或不满足约束。 */
final class InvalidAnalyticsRequest extends RuntimeException {
    InvalidAnalyticsRequest(String message) {
        super(message);
    }

    InvalidAnalyticsRequest(String message, Throwable cause) {
        super(message, cause);
    }
}
