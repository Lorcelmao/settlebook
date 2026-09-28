package dev.settlebook.gateway.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * VNPay checksum (API 2.1.0): HMAC-SHA512 over the {@code vnp_*} parameters sorted by name, each written
 * as {@code urlencode(name)=urlencode(value)} and joined with {@code &}. Empty values and the hash fields
 * themselves are excluded. The same canonical string is used as the query string of the payment URL.
 */
public final class VnpaySigner {

	static final String SECURE_HASH = "vnp_SecureHash";
	static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

	private static final String ALGORITHM = "HmacSHA512";

	private final SecretKeySpec key;

	public VnpaySigner(String hashSecret) {
		this.key = new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	/** The canonical, URL-encoded parameter string that is signed. */
	public static String canonicalQuery(Map<String, String> params) {
		return new TreeMap<>(params).entrySet()
			.stream()
			.filter(e -> e.getKey().startsWith("vnp_"))
			.filter(e -> !e.getKey().equals(SECURE_HASH) && !e.getKey().equals(SECURE_HASH_TYPE))
			.filter(e -> e.getValue() != null && !e.getValue().isEmpty())
			.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
			.collect(Collectors.joining("&"));
	}

	/** Lower-case hex HMAC-SHA512 of {@link #canonicalQuery(Map)}. */
	public String sign(Map<String, String> params) {
		return HexFormat.of().formatHex(hmac(canonicalQuery(params)));
	}

	/**
	 * Verifies {@code vnp_SecureHash} in constant time (the comparison does not stop at the first differing
	 * byte, so response timing does not reveal how much of a forged hash was correct).
	 */
	public boolean isValid(Map<String, String> params) {
		String provided = params.get(SECURE_HASH);
		if (provided == null || provided.isBlank()) {
			return false;
		}
		byte[] expected = sign(params).getBytes(StandardCharsets.US_ASCII);
		byte[] actual = provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expected, actual);
	}

	private byte[] hmac(String data) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HMAC-SHA512 unavailable", ex);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
