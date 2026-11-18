
package com.nextcart.backend.admin;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = AdminAccess.class)
public class AdminApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail requestRejected(ResponseStatusException exception) {
        return exception.getBody();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidFields(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldError();
        String message = field == null ? "Check the form values."
                : "Check " + field.getField() + ". " + field.getDefaultMessage() + ".";
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), message);
    }
}
