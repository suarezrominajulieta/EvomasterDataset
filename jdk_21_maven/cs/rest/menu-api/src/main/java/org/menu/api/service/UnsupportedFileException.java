package org.menu.api.service;

import org.springframework.http.HttpStatus;

/** The uploaded part is of a type this API does not take: 415. */
public class UnsupportedFileException extends ApiException {

    public UnsupportedFileException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
