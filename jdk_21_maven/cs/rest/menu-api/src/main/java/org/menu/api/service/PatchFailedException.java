package org.menu.api.service;

import org.springframework.http.HttpStatus;

/**
 * A JSON Patch that could not be applied to the document as it stands: a failed {@code test},
 * or a pointer into a field that is not there. Distinct from a malformed patch, which is a 400.
 */
public class PatchFailedException extends ApiException {

    public PatchFailedException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
