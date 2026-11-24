package org.menu.api.service;

import org.springframework.http.HttpStatus;

/** The request is malformed: 400. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
