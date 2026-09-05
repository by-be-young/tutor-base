package com.tutorbase.wrongbook;

import com.tutorbase.shared.http.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
final class WrongBookHttpExceptionHandler {

    @ExceptionHandler(WrongBookController.LearnerContextRequired.class)
    ProblemDetail learnerRequired(RuntimeException exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.FORBIDDEN, "learner_context_required", "账户未关联学习者",
                "当前账户未关联学习者身份，无法执行该操作。", request);
    }

    @ExceptionHandler(WrongBookService.EntryNotFound.class)
    ProblemDetail notFound(RuntimeException exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.NOT_FOUND, "wrong_book_entry_not_found", "错题不存在",
                "没有找到当前学习者的指定错题。", request);
    }

    @ExceptionHandler(WrongBookService.AlreadyCollected.class)
    ProblemDetail alreadyCollected(RuntimeException exception, HttpServletRequest request) {
        return ApiProblem.create(HttpStatus.CONFLICT, "wrong_book_entry_exists", "错题已经收集",
                "该题已经在当前学习者的错题本中。", request);
    }
}
