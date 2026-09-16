package com.hms.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DjangoPbkdf2PasswordEncoderTests {

    private final DjangoPbkdf2PasswordEncoder encoder = new DjangoPbkdf2PasswordEncoder();

    // Generated with Django's own PBKDF2PasswordHasher (see HMS repo,
    // python -c "from django.contrib.auth.hashers import PBKDF2PasswordHasher; ..."),
    // not fabricated — proves this encoder is byte-for-byte compatible with
    // what Django actually writes to hospital_user.password.
    private static final String REAL_DJANGO_HASH =
            "pbkdf2_sha256$100000$testsalt1234$U336d+ZNBcXc1jLCB64wk8cGgFAFodi10z+Bbrh+fRk=";

    @Test
    void matchesCorrectPasswordAgainstRealDjangoHash() {
        assertThat(encoder.matches("CorrectHorseBatteryStaple", REAL_DJANGO_HASH)).isTrue();
    }

    @Test
    void rejectsWrongPasswordAgainstRealDjangoHash() {
        assertThat(encoder.matches("WrongPassword", REAL_DJANGO_HASH)).isFalse();
    }

    @Test
    void rejectsHashInAnUnrecognizedFormat() {
        assertThat(encoder.matches("anything", "bcrypt$notdjango$format")).isFalse();
    }

    @Test
    void encodeThenMatchesRoundTrips() {
        String encoded = encoder.encode("AnotherPassword1");
        assertThat(encoder.matches("AnotherPassword1", encoded)).isTrue();
        assertThat(encoder.matches("WrongOne", encoded)).isFalse();
    }
}
