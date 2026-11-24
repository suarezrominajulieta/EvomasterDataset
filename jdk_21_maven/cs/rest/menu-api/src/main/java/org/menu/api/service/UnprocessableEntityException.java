package org.menu.api.service;

import org.springframework.http.HttpStatus;

/** Syntactically fine, semantically refused by a business rule: 422. */
public class UnprocessableEntityException extends ApiException {

    public UnprocessableEntityException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
