package github.reactivity512.meetup.web;

import github.reactivity512.meetup.service.MeetupNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JSON error responses for the status API. Registered before
 * {@link GlobalExceptionHandler} so API clients never receive an HTML error page.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MeetupApiController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MeetupNotFoundException.class)
    public ProblemDetail notFound(MeetupNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                e.getMessage() == null ? "Митинг не найден" : e.getMessage());
        problem.setTitle("Митинг не найден");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unhandled API exception", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        problem.setTitle("Внутренняя ошибка");
        return problem;
    }
}
