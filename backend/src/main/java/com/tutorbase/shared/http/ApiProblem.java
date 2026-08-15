package com.tutorbase.shared.http;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblem {

    private static final String TYPE_BASE = "https://api.be-young.top/problems/";

    private ApiProblem() {
    }

    public static ProblemDetail create(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.replace('_', '-')));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("traceId", traceId(request));
        return problem;
    }

    private static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? "unavailable" : value.toString();
    }
}
