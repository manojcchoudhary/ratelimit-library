package com.lycosoft.ratelimit.storage;

public class SecureStorageException extends StorageException {

    private final String internalMessage;
    private final String publicMessage;

    public SecureStorageException(String publicMsg, String internalMsg, Throwable cause) {
        super(internalMsg, cause);
        this.publicMessage = publicMsg;
        this.internalMessage = internalMsg;
    }

    public SecureStorageException(String publicMsg, String internalMsg) {
        super(internalMsg);
        this.publicMessage = publicMsg;
        this.internalMessage = internalMsg;
    }

    public SecureStorageException(String message) {
        super(message);
        this.publicMessage = message;
        this.internalMessage = message;
    }

    @Override
    public String getMessage() {
        if (isProduction()) {
            return publicMessage;  // "Rate limit service temporarily unavailable"
        }
        return internalMessage;    // Full details for debugging
    }

    private boolean isProduction() {
        String env = System.getenv("ENVIRONMENT");
        return "production".equalsIgnoreCase(env);
    }

}
