package com.nextcart.backend.store;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = StoreAccess.class)
class StoreErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail rejected(ResponseStatusException exception) {
        return exception.getBody();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldError();
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), field == null
                ? "Check the form values." : field.getField() + " " + field.getDefaultMessage());
    }
}
