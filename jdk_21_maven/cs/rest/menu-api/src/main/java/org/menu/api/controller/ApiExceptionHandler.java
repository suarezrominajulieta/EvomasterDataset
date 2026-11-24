package org.menu.api.controller;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import org.menu.api.dto.ErrorDto;
import org.menu.api.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every <em>expected</em> failure into an {@link ErrorDto}.
 *
 * <p>There is deliberately no {@code @ExceptionHandler(Exception.class)} here. Anything this
 * class does not name travels on and becomes a bare 500, which is exactly what a fuzzer pointed
 * at this application is supposed to find and report. Catching everything would hide the faults
 * along with the noise.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Everything the services raise on purpose. Each subclass carries its own status. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDto> onApiException(ApiException e, HttpServletRequest request) {
        return error(e.getStatus(), e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> onBeanValidation(MethodArgumentNotValidException e,
                                                     HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, firstViolation(e), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> onUnreadableBody(HttpMessageNotReadableException e,
                                                     HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, unreadableBodyMessage(e), request);
    }

    /**
     * An id in the path that is not even a UUID names nothing that could be stored, so it gets
     * the same 404 as a well formed id that is absent. A query parameter is part of the request
     * rather than of the resource, so that stays a 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDto> onTypeMismatch(MethodArgumentTypeMismatchException e,
                                                   HttpServletRequest request) {
        if (e.getParameter().hasParameterAnnotation(
                org.springframework.web.bind.annotation.PathVariable.class)) {
            return error(HttpStatus.NOT_FOUND, "no resource named " + e.getValue(), request);
        }
        return error(HttpStatus.BAD_REQUEST,
                e.getName() + " has an unacceptable value: " + e.getValue(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDto> onMissingParameter(MissingServletRequestParameterException e,
                                                       HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, e.getParameterName() + " is required", request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorDto> onMissingPart(MissingServletRequestPartException e,
                                                  HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "part " + e.getRequestPartName() + " is required",
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> onUnknownResource(NoResourceFoundException e,
                                                      HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "no resource at " + e.getResourcePath(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDto> onMethodNotAllowed(HttpRequestMethodNotSupportedException e,
                                                       HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorDto> onUnsupportedMediaType(HttpMediaTypeNotSupportedException e,
                                                           HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "this path does not take " + e.getContentType(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorDto> onNotAcceptable(HttpServletRequest request) {
        return error(HttpStatus.NOT_ACCEPTABLE, "this API produces application/json", request);
    }

    /**
     * A multipart body the container could not take apart at all, which includes the one case
     * the application does not decide for itself: a request past the configured size limits.
     * Those limits are set high precisely so that this stays rare and the refusals in
     * {@code FileValidationService} do the work.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorDto> onMultipart(MultipartException e, HttpServletRequest request) {
        if (rootCauseMentions(e, "size") || rootCauseMentions(e, "exceed")) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "the multipart request is too large",
                    request);
        }
        return error(HttpStatus.BAD_REQUEST, "the multipart body could not be read", request);
    }

    private static boolean rootCauseMentions(Throwable e, String word) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains(word)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * Jackson's own messages name internal classes and change between versions, so they are
     * replaced with the field that tripped it up.
     */
    private static String unreadableBodyMessage(HttpMessageNotReadableException e) {
        if (!(e.getCause() instanceof JacksonException)) {
            return "the body could not be read";
        }
        String field = mappedField(e.getCause());
        return field == null
                ? "the body could not be read as JSON"
                : field + " does not carry a value of the expected type";
    }

    /** Dotted path of the offending field, or null when Jackson did not report one. */
    private static String mappedField(Throwable cause) {
        if (!(cause instanceof JsonMappingException mapping)) {
            return null;
        }
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : mapping.getPath()) {
            if (reference.getFieldName() == null) {
                continue;
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(reference.getFieldName());
        }
        return path.isEmpty() ? null : path.toString();
    }

    private static String firstViolation(MethodArgumentNotValidException e) {
        ObjectError first = e.getBindingResult().getAllErrors().stream().findFirst().orElse(null);
        if (first == null) {
            return "the document is not valid";
        }
        if (first instanceof FieldError fieldError) {
            return fieldError.getField() + " " + fieldError.getDefaultMessage();
        }
        return first.getDefaultMessage();
    }

    private static ResponseEntity<ErrorDto> error(HttpStatus status,
                                                  String message,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErrorDto(status.value(), status.getReasonPhrase(), message,
                        request.getRequestURI()));
    }
}
