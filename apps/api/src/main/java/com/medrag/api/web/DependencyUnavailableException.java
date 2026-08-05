package com.medrag.api.web;

public class DependencyUnavailableException extends RuntimeException {
    private final String code;

    public DependencyUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
