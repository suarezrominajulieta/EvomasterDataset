package org.movies.xml.service;

/**
 * Thrown when an id does not match any stored movie. Rendered as 404.
 */
public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(Long id) {
        super("movie " + id + " not found");
    }
}
