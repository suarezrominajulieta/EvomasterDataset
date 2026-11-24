package org.menu.api.service;

import org.springframework.http.HttpStatus;

/** Something was sent twice where once was expected: 409. */
public class DuplicateException extends ApiException {

    public DuplicateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
