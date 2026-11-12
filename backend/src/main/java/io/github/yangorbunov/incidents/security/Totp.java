package io.github.yangorbunov.incidents.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238) поверх HMAC-SHA1 без внешних зависимостей.
 * Совместим с Google Authenticator, Яндекс Ключом, Aegis и т.п.
 */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 160-битный секрет в Base32. */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static String otpauthUri(String issuer, String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
    }

    /** Проверка кода с допуском ±1 шаг (рассинхронизация часов). */
    public static boolean verify(String secret, String code, Instant now) {
        if (secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key = base32Decode(secret);
        long counter = now.getEpochSecond() / STEP_SECONDS;
        boolean ok = false;
        for (long c = counter - 1; c <= counter + 1; c++) {
            // без короткого замыкания — время проверки не зависит от того, какой шаг совпал
            ok |= MessageDigest.isEqual(code(key, c, 6).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII));
        }
        return ok;
    }

    static String code(byte[] key, long counter, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char ch : clean.toCharArray()) {
            int v = BASE32.indexOf(ch);
            if (v < 0) {
                throw new IllegalArgumentException("Некорректный символ Base32: " + ch);
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
