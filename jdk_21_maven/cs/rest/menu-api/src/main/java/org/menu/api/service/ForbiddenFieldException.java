package org.menu.api.service;

import org.springframework.http.HttpStatus;

/** The request is well formed but reaches for something it is not allowed to touch: 403. */
public class ForbiddenFieldException extends ApiException {

    public ForbiddenFieldException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
