package fr.efrei.bookingservice.exception;

import org.springframework.http.HttpStatus;
import fr.efrei.bookingservice.domain.BookingAccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingAccessDeniedException.class)
    public ProblemDetail handleForbidden(BookingAccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The booking conflicts with current availability or was modified concurrently. Refresh and retry.");
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ProblemDetail handleNotFound(BookingNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setDetail("Validation failed");
        problem.setProperty("errors", ex.getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
