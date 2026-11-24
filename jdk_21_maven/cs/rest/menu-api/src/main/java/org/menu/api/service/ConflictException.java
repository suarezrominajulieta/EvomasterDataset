package org.menu.api.service;

import org.springframework.http.HttpStatus;

/** The request makes sense but disagrees with the stored state: 409. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
