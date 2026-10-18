
package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoApplicationTests {

    // ============================================================
    // CREATE RSA SHA-256 SIGNATURE
    // ============================================================
    public static String createSbiCoreKey(
            PrivateKey privateKey,
            String acceptLanguage,
            String timestamp,
            String clientId,
            String requestId) throws Exception {

        String canonicalData = buildCanonicalData(
                acceptLanguage,
                timestamp,
                clientId,
                requestId);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(canonicalData.getBytes(StandardCharsets.UTF_8));

        // Standard Base64 encoding
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    // ============================================================
    // VERIFY RSA SHA-256 SIGNATURE
    // ============================================================
    public static boolean verifySbiCoreKey(
            PublicKey publicKey,
            String receivedSignature,
            String acceptLanguage,
            String timestamp,
            String clientId,
            String requestId) throws Exception {

        if (receivedSignature == null || receivedSignature.isBlank()) {
            return false;
        }

        final byte[] signatureBytes;

        try {
            // Must match Base64.getEncoder() in createSbiCoreKey()
            signatureBytes = Base64.getDecoder().decode(receivedSignature);
        } catch (IllegalArgumentException ex) {
            // Malformed Base64 is not a valid signature
            return false;
        }

        String canonicalData = buildCanonicalData(
                acceptLanguage,
                timestamp,
                clientId,
                requestId);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(canonicalData.getBytes(StandardCharsets.UTF_8));

        return signature.verify(signatureBytes);
    }

    // ============================================================
    // BUILD CANONICAL REQUEST DATA
    // The sender and verifier must use exactly the same format.
    // ============================================================
    private static String buildCanonicalData(
            String acceptLanguage,
            String timestamp,
            String clientId,
            String requestId) {

        return "Accept-Language:" + acceptLanguage + "\n"
                + "X-REQ-TIMESTAMP:" + timestamp + "\n"
                + "X-CLIENT-ID:" + clientId + "\n"
                + "X-REQ-ID:" + requestId;
    }

    // ============================================================
    // GENERATE RSA 2048 KEY PAIR FOR TESTS
    // ============================================================
    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    // ============================================================
    // TEST 1: Valid signature succeeds
    // ============================================================
    @Test
    void testCreateAndVerifySbiCoreKey() throws Exception {

        KeyPair keyPair = generateKeyPair();

        String acceptLanguage = "en-US";
        String timestamp = "2026-09-25T23:57:00+07:00";
        String clientId = "CLIENT-001";
        String requestId = "REQ-001";

        String encodedSignature = createSbiCoreKey(
                keyPair.getPrivate(),
                acceptLanguage,
                timestamp,
                clientId,
                requestId);

        boolean verified = verifySbiCoreKey(
                keyPair.getPublic(),
                encodedSignature,
                acceptLanguage,
                timestamp,
                clientId,
                requestId);

        assertTrue(verified);
    }

    // ============================================================
    // TEST 2: Modified request ID fails verification
    // ============================================================
    @Test
    void testRequestIdTampered() throws Exception {

        KeyPair keyPair = generateKeyPair();

        String encodedSignature = createSbiCoreKey(
                keyPair.getPrivate(),
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-001");

        boolean verified = verifySbiCoreKey(
                keyPair.getPublic(),
                encodedSignature,
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-999");

        assertFalse(verified);
    }

    // ============================================================
    // TEST 3: Modified client ID fails verification
    // ============================================================
    @Test
    void testClientIdTampered() throws Exception {

        KeyPair keyPair = generateKeyPair();

        String encodedSignature = createSbiCoreKey(
                keyPair.getPrivate(),
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-001");

        boolean verified = verifySbiCoreKey(
                keyPair.getPublic(),
                encodedSignature,
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-999",
                "REQ-001");

        assertFalse(verified);
    }

    // ============================================================
    // TEST 4: Modified timestamp fails verification
    // ============================================================
    @Test
    void testTimestampTampered() throws Exception {

        KeyPair keyPair = generateKeyPair();

        String encodedSignature = createSbiCoreKey(
                keyPair.getPrivate(),
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-001");

        boolean verified = verifySbiCoreKey(
                keyPair.getPublic(),
                encodedSignature,
                "en-US",
                "2026-09-25T23:58:00+07:00",
                "CLIENT-001",
                "REQ-001");

        assertFalse(verified);
    }

    // ============================================================
    // TEST 5: Modified Accept-Language fails verification
    // ============================================================
    @Test
    void testAcceptLanguageTampered() throws Exception {

        KeyPair keyPair = generateKeyPair();

        String encodedSignature = createSbiCoreKey(
                keyPair.getPrivate(),
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-001");

        boolean verified = verifySbiCoreKey(
                keyPair.getPublic(),
                encodedSignature,
                "km-KH",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-001");

        assertFalse(verified);
    }

    // ============================================================
    // TEST 6: Malformed Base64 signature is rejected
    // ============================================================
    @Test
    void testInvalidSignature() throws Exception {

        KeyPair keyPair = generateKeyPair();

        boolean verified = verifySbiCoreKey(
                keyPair.getPublic(),
                "INVALID_SIGNATURE",
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-001");

        assertFalse(verified);
    }

    // ============================================================
    // TEST 7: Valid Base64 but incorrect signature is rejected
    // ============================================================
    @Test
    void testWrongSignatureBytes() throws Exception {

        KeyPair keyPair = generateKeyPair();

        // Valid Base64 encoding, but not a valid RSA signature.
        String invalidSignature = Base64.getEncoder()
                .encodeToString(new byte[256]);

        boolean verified = verifySbiCoreKey(
                keyPair.getPublic(),
                invalidSignature,
                "en-US",
                "2026-09-25T23:57:00+07:00",
                "CLIENT-001",
                "REQ-001");

        assertFalse(verified);
    }
}
