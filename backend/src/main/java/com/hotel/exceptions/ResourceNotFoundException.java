package com.hotel.exceptions;

/**
 * Thrown when an entity is not found OR the caller has no tenant access to it.
 * Always maps to HTTP 404 to prevent IDOR enumeration.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}