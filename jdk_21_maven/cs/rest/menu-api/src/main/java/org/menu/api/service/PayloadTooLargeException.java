package org.menu.api.service;

import org.springframework.http.HttpStatus;

/**
 * Too much came in: 413. Checked by the application well below the container's own limits, so
 * that the refusal is a branch in this code rather than a generic container error.
 */
public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}
