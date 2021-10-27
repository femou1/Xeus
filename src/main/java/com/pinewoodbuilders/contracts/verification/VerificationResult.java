package com.pinewoodbuilders.contracts.verification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VerificationResult {

    private VerificationEntity verificationEntity;
    private boolean success;
    private String message;

    public VerificationResult(boolean success, VerificationEntity ve, @Nullable String message) {
        this.verificationEntity = ve;
        this.success = success;
        this.message = message;
    }

    public VerificationResult(boolean success, @NotNull String message) {
        this.message = message;
        this.success = success;
        this.verificationEntity = null;
    }

    public VerificationResult(boolean success, VerificationEntity ve) {
        this.success = success;
        this.verificationEntity = ve;
        this.message = "Message was not set... | Status is " + success;
    }

    public VerificationResult(boolean blacklisted) {
        this.success = blacklisted;
        this.verificationEntity = null;
        this.message = null;
    }

    public VerificationEntity getVerificationEntity() {
        return verificationEntity;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
