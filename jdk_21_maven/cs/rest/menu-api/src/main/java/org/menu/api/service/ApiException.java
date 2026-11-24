package org.menu.api.service;

import org.springframework.http.HttpStatus;

/**
 * An expected failure: something the client did that this API has a considered answer for.
 *
 * <p>Every subclass fixes its own status, and {@code ApiExceptionHandler} turns the lot into
 * {@code ErrorDto}. Anything that is <em>not</em> one of these is a genuine fault and is left to
 * surface as a 500, which is the whole point of pointing a fuzzer at this application.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
