package github.reactivity512.meetup.web;

import github.reactivity512.meetup.service.MeetupNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MeetupNotFoundException.class)
    public ModelAndView notFound(MeetupNotFoundException e) {
        return errorPage(HttpStatus.NOT_FOUND, "Митинг не найден", e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView resourceNotFound(NoResourceFoundException e) {
        return errorPage(HttpStatus.NOT_FOUND, "Страница не найдена", "Проверьте адрес страницы.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView uploadTooLarge(MaxUploadSizeExceededException e) {
        return errorPage(HttpStatus.PAYLOAD_TOO_LARGE, "Файл слишком большой",
                "Увеличьте spring.servlet.multipart.max-file-size или загрузите файл меньшего размера.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка",
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private ModelAndView errorPage(HttpStatus status, String title, String message) {
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("error", title);
        modelAndView.addObject("message", message);
        return modelAndView;
    }
}
