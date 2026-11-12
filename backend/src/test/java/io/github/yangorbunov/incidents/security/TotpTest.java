package io.github.yangorbunov.incidents.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpTest {

    /** Тестовые векторы RFC 6238, приложение B (SHA-1, 8 цифр). */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rfc6238Vectors() {
        assertEquals("94287082", Totp.code(RFC_KEY, 59L / 30, 8));
        assertEquals("07081804", Totp.code(RFC_KEY, 1111111109L / 30, 8));
        assertEquals("14050471", Totp.code(RFC_KEY, 1111111111L / 30, 8));
        assertEquals("89005924", Totp.code(RFC_KEY, 1234567890L / 30, 8));
        assertEquals("69279037", Totp.code(RFC_KEY, 2000000000L / 30, 8));
    }

    @Test
    void base32RoundTrip() {
        byte[] data = RFC_KEY;
        String encoded = Totp.base32Encode(data);
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", encoded);
        assertArrayEquals(data, Totp.base32Decode(encoded));
    }

    @Test
    void verifyAcceptsAdjacentStepAndRejectsOthers() {
        String secret = Totp.base32Encode(RFC_KEY);
        Instant t = Instant.ofEpochSecond(1234567890L);
        String current = Totp.code(RFC_KEY, t.getEpochSecond() / 30, 6);
        String previous = Totp.code(RFC_KEY, t.getEpochSecond() / 30 - 1, 6);
        String old = Totp.code(RFC_KEY, t.getEpochSecond() / 30 - 3, 6);

        assertTrue(Totp.verify(secret, current, t));
        assertTrue(Totp.verify(secret, previous, t));
        assertFalse(Totp.verify(secret, old, t));
        assertFalse(Totp.verify(secret, "12345", t));
        assertFalse(Totp.verify(secret, null, t));
    }

    @Test
    void otpauthUriIsEncoded() {
        String uri = Totp.otpauthUri("Incident Analytics", "petrov", "ABC");
        assertEquals("otpauth://totp/Incident%20Analytics:petrov?secret=ABC&issuer=Incident%20Analytics"
                + "&algorithm=SHA1&digits=6&period=30", uri);
    }
}
