package org.menu.api.service;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException("no " + what + " with id " + id);
    }
}
