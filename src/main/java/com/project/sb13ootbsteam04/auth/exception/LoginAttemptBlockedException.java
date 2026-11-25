package com.project.sb13ootbsteam04.auth.exception;

public class LoginAttemptBlockedException
        extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginAttemptBlockedException(
            String message,
            long retryAfterSeconds
    ) {
        super(message);
        this.retryAfterSeconds = Math.max(
                1L,
                retryAfterSeconds
        );
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
