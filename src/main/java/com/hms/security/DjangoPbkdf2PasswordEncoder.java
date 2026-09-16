package com.hms.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Verifies (and can produce) password hashes in Django's default format —
 * {@code pbkdf2_sha256$<iterations>$<salt>$<base64hash>}, produced by
 * {@code django.contrib.auth.hashers.PBKDF2PasswordHasher} (the default
 * since HMS's settings.py sets no custom PASSWORD_HASHERS). This is what
 * lets an existing Django-created login authenticate against this Spring
 * Boot app with the exact same password, no reset/migration needed.
 */
public class DjangoPbkdf2PasswordEncoder implements PasswordEncoder {

    private static final String ALGORITHM_PREFIX = "pbkdf2_sha256";
    private static final int KEY_LENGTH_BITS = 256;
    // Only used for hashes this app creates itself; verifying an existing
    // Django hash always uses whatever iteration count is embedded in it.
    private static final int DEFAULT_ITERATIONS_FOR_NEW_HASHES = 600_000;

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] saltBytes = new byte[12];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().withoutPadding().encodeToString(saltBytes);
        String hash = pbkdf2Base64(rawPassword, salt, DEFAULT_ITERATIONS_FOR_NEW_HASHES);
        return ALGORITHM_PREFIX + "$" + DEFAULT_ITERATIONS_FOR_NEW_HASHES + "$" + salt + "$" + hash;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        String[] parts = encodedPassword.split("\\$", 4);
        if (parts.length != 4 || !ALGORITHM_PREFIX.equals(parts[0])) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        String salt = parts[2];
        String expectedHash = parts[3];

        String actualHash = pbkdf2Base64(rawPassword, salt, iterations);
        return MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String pbkdf2Base64(CharSequence rawPassword, String salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    rawPassword.toString().toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    iterations,
                    KEY_LENGTH_BITS
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 not available on this JVM", e);
        }
    }
}
