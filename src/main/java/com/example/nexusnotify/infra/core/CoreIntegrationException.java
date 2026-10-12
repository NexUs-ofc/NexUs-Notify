package com.example.nexusnotify.infra.core;

public class CoreIntegrationException extends RuntimeException {

    public CoreIntegrationException(String message) {
        super(message);
    }

    public CoreIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
