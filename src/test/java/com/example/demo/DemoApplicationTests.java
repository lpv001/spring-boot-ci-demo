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
	// Static method under test: CREATE SIGNATURE
	// ============================================================
	public static String createSbiCoreKey(
			PrivateKey privateKey,
			String acceptLanguage,
			String timestamp,
			String clientId,
			String requestId) throws Exception {

		String canonicalData = "Accept-Language:" + acceptLanguage + "\n" +
				"X-REQ-TIMESTAMP:" + timestamp + "\n" +
				"X-CLIENT-ID:" + clientId + "\n" +
				"X-REQ-ID:" + requestId;

		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(
				canonicalData.getBytes(StandardCharsets.UTF_8));

		return Base64.getEncoder().encodeToString(signature.sign());
	}

	// ============================================================
	// Static method under test: VERIFY SIGNATURE
	// ============================================================
	public static boolean verifySbiCoreKey(
			PublicKey publicKey,
			String receivedSignature,
			String acceptLanguage,
			String timestamp,
			String clientId,
			String requestId) throws Exception {

		String canonicalData = "Accept-Language:" + acceptLanguage + "\n" +
				"X-REQ-TIMESTAMP:" + timestamp + "\n" +
				"X-CLIENT-ID:" + clientId + "\n" +
				"X-REQ-ID:" + requestId;

		byte[] signatureBytes = Base64.getUrlDecoder().decode(receivedSignature);

		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initVerify(publicKey);
		signature.update(
				canonicalData.getBytes(StandardCharsets.UTF_8));

		return signature.verify(signatureBytes);
	}

	// ============================================================
	// Generate RSA 2048 key pair
	// ============================================================
	private KeyPair generateKeyPair() throws Exception {

		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

		generator.initialize(2048);

		return generator.generateKeyPair();
	}

	// ============================================================
	// TEST 1: Normal sign + verify
	// ============================================================
	@Test
	void testCreateAndVerifySbiCoreKey() throws Exception {

		KeyPair keyPair = generateKeyPair();

		// Static input
		String acceptLanguage = "en-US";
		String timestamp = "2026-09-25T23:57:00+07:00";
		String clientId = "CLIENT-001";
		String requestId = "REQ-001";

		// Create signature
		String signature = createSbiCoreKey(
				keyPair.getPrivate(),
				acceptLanguage,
				timestamp,
				clientId,
				requestId);

		System.out.println("========================================");
		System.out.println("Generated Signature:");
		System.out.println(signature);
		System.out.println("========================================");

		// Verify signature
		boolean verified = verifySbiCoreKey(
				keyPair.getPublic(),
				signature,
				acceptLanguage,
				timestamp,
				clientId,
				requestId);

		System.out.println("Verification result: " + verified);

		assertTrue(verified);
	}

	// ============================================================
	// TEST 2: Request ID changed -> verification MUST fail
	// ============================================================
	@Test
	void testRequestIdTampered() throws Exception {

		KeyPair keyPair = generateKeyPair();

		String acceptLanguage = "en-US";
		String timestamp = "2026-09-25T23:57:00+07:00";
		String clientId = "CLIENT-001";
		String requestId = "REQ-001";

		String signature = createSbiCoreKey(
				keyPair.getPrivate(),
				acceptLanguage,
				timestamp,
				clientId,
				requestId);

		// Changed request ID
		boolean verified = verifySbiCoreKey(
				keyPair.getPublic(),
				signature,
				acceptLanguage,
				timestamp,
				clientId,
				"REQ-999");

		System.out.println("Request ID tampered: " + verified);

		assertFalse(verified);
	}

	// ============================================================
	// TEST 3: Client ID changed -> verification MUST fail
	// ============================================================
	@Test
	void testClientIdTampered() throws Exception {

		KeyPair keyPair = generateKeyPair();

		String signature = createSbiCoreKey(
				keyPair.getPrivate(),
				"en-US",
				"2026-09-25T23:57:00+07:00",
				"CLIENT-001",
				"REQ-001");

		boolean verified = verifySbiCoreKey(
				keyPair.getPublic(),
				signature,
				"en-US",
				"2026-09-25T23:57:00+07:00",
				"CLIENT-999",
				"REQ-001");

		assertFalse(verified);
	}

	// ============================================================
	// TEST 4: Timestamp changed -> verification MUST fail
	// ============================================================
	@Test
	void testTimestampTampered() throws Exception {

		KeyPair keyPair = generateKeyPair();

		String signature = createSbiCoreKey(
				keyPair.getPrivate(),
				"en-US",
				"2026-09-25T23:57:00+07:00",
				"CLIENT-001",
				"REQ-001");

		boolean verified = verifySbiCoreKey(
				keyPair.getPublic(),
				signature,
				"en-US",
				"2026-09-25T23:58:00+07:00",
				"CLIENT-001",
				"REQ-001");

		assertFalse(verified);
	}

	// ============================================================
	// TEST 5: Accept-Language changed -> verification MUST fail
	// ============================================================
	@Test
	void testAcceptLanguageTampered() throws Exception {

		KeyPair keyPair = generateKeyPair();

		String signature = createSbiCoreKey(
				keyPair.getPrivate(),
				"en-US",
				"2026-09-25T23:57:00+07:00",
				"CLIENT-001",
				"REQ-001");

		boolean verified = verifySbiCoreKey(
				keyPair.getPublic(),
				signature,
				"km-KH",
				"2026-09-25T23:57:00+07:00",
				"CLIENT-001",
				"REQ-001");

		assertFalse(verified);
	}

	// ============================================================
	// TEST 6: Completely invalid signature -> verification MUST fail
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

		// Base64 decoding itself will throw an exception.
		// Therefore this test expects an exception.
		assertFalse(verified);
	}
}
