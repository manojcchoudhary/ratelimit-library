package com.lycosoft.ratelimit.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Masks personally identifiable information (PII) in rate limit keys using SHA-256 hashing.
 * 
 * <p>This class provides GDPR/CCPA compliant key masking for audit logs.
 * Instead of logging raw keys (which may contain user IDs, emails, etc.),
 * it logs a salted hash.
 * 
 * <p><b>Example:</b>
 * <pre>
 * Input:  "user:john.doe@example.com:/api/orders"
 * Output: "sha256:a3f5b2c9...7e8d"
 * </pre>
 * 
 * @since 1.0.0
 */
public class PiiSafeKeyMasker {
    
    private static final String ALGORITHM = "SHA-256";
    private static final String PREFIX = "sha256:";
    
    private final String salt;

    private final ThreadLocal<MessageDigest> digestThreadLocal;
    
    /**
     * Creates a key masker with auto-generated salt.
     */
    public PiiSafeKeyMasker() {
        this(null);
    }
    
    /**
     * Creates a key masker with specified salt.
     * 
     * @param salt the salt to use (if null, a random UUID is generated)
     */
    public PiiSafeKeyMasker(String salt) {
        this.salt = (salt != null && !salt.isEmpty()) ? salt : UUID.randomUUID().toString();
        this.digestThreadLocal = ThreadLocal.withInitial(() -> {
            try { return MessageDigest.getInstance(ALGORITHM); }
            catch (NoSuchAlgorithmException e) { throw new RuntimeException(ALGORITHM + " algorithm not available", e); }
        });
    }
    
    /**
     * Masks a key using SHA-256 hashing.
     * 
     * <p>The output format is: "sha256:{first 8 chars}...{last 4 chars}"
     * 
     * @param rawKey the raw key to mask
     * @return the masked key
     */
    public String maskKey(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        
        // Calculate SHA-256 hash with salt
        String fullHash = calculateHash(rawKey);
        
        // Return shortened representation for logs
        // Format: sha256:a3f5b2c9...7e8d
        if (fullHash.length() >= 12) {
            return PREFIX + fullHash.substring(0, 8) + "..." + fullHash.substring(fullHash.length() - 4);
        } else {
            return PREFIX + fullHash;
        }
    }
    
    /**
     * Calculates the full SHA-256 hash of the key with salt.
     * 
     * @param rawKey the raw key
     * @return the full hash in hexadecimal
     */
    public String calculateHash(String rawKey) {
        if (rawKey == null) return null;
        MessageDigest digest = digestThreadLocal.get();
        digest.reset();
        digest.update((rawKey + salt).getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest.digest());
    }
    
    /**
     * Converts byte array to hexadecimal string.
     * 
     * @param bytes the byte array
     * @return hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
    
    /**
     * Gets the salt used by this masker (for configuration persistence).
     * 
     * @return the salt value
     */
    public String getSalt() {
        return salt;
    }
    
    /**
     * Verifies if a masked key matches a raw key.
     * 
     * <p>This is useful for debugging or verification purposes.
     * 
     * @param maskedKey the masked key (e.g., "sha256:a3f5b2c9...7e8d")
     * @param rawKey the raw key to verify
     * @return true if the raw key produces the same masked key
     */
    public boolean verify(String maskedKey, String rawKey) {
        if (maskedKey == null || rawKey == null) {
            return false;
        }
        
        String calculatedMask = maskKey(rawKey);
        return maskedKey.equals(calculatedMask);
    }
}
