package dev.settlebook.gateway.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VnpaySignerTest {

	private static final String SECRET = "test-hash-secret-not-a-real-key";

	/**
	 * Golden vector computed independently (Python {@code hmac} + {@code urllib.parse.quote_plus}, i.e. the
	 * PHP {@code urlencode} scheme of VNPay's sample code), not by this class.
	 */
	private static final String EXPECTED_CANONICAL = "vnp_Amount=15000000&vnp_Command=pay"
			+ "&vnp_CreateDate=20260929003000&vnp_CurrCode=VND&vnp_ExpireDate=20260929004500&vnp_IpAddr=127.0.0.1"
			+ "&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+don+hang+0f8fad5bd9cb469fa165708e9a2b3c4d&vnp_OrderType=other"
			+ "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fvnpay%2Freturn&vnp_TmnCode=TESTTMN1"
			+ "&vnp_TxnRef=0f8fad5bd9cb469fa165708e9a2b3c4d&vnp_Version=2.1.0";

	private static final String EXPECTED_HASH = "d9c109c7c1f2b840cc10b4482983cafcb1f1db0aacb23dde0f43b3144671fe4e"
			+ "dfc80cf39a1b28eca3c5fff827a5da461fbb6ccbb7eee230b2ebb0df2da7aaac";

	private final VnpaySigner signer = new VnpaySigner(SECRET);

	@Test
	void matchesIndependentlyComputedGoldenVector() {
		assertThat(VnpaySigner.canonicalQuery(goldenParams())).isEqualTo(EXPECTED_CANONICAL);
		assertThat(signer.sign(goldenParams())).isEqualTo(EXPECTED_HASH);
	}

	@Test
	void ignoresHashFieldsEmptyValuesAndNonVnpParameters() {
		Map<String, String> params = goldenParams();
		params.put("vnp_SecureHash", "whatever");
		params.put("vnp_SecureHashType", "HMACSHA512");
		params.put("unrelated", "value");

		assertThat(signer.sign(params)).isEqualTo(EXPECTED_HASH);
	}

	@Test
	void acceptsCorrectHashInEitherCase() {
		Map<String, String> params = goldenParams();
		params.put("vnp_SecureHash", EXPECTED_HASH.toUpperCase(Locale.ROOT));

		assertThat(signer.isValid(params)).isTrue();
	}

	@Test
	void rejectsTamperedParameterMissingHashAndWrongSecret() {
		Map<String, String> tampered = goldenParams();
		tampered.put("vnp_SecureHash", EXPECTED_HASH);
		tampered.put("vnp_Amount", "15000100");
		assertThat(signer.isValid(tampered)).isFalse();

		assertThat(signer.isValid(goldenParams())).isFalse();

		Map<String, String> signedWithOtherKey = goldenParams();
		signedWithOtherKey.put("vnp_SecureHash", new VnpaySigner("another-secret").sign(goldenParams()));
		assertThat(signer.isValid(signedWithOtherKey)).isFalse();
	}

	static Map<String, String> goldenParams() {
		Map<String, String> params = new HashMap<>();
		params.put("vnp_Version", "2.1.0");
		params.put("vnp_Command", "pay");
		params.put("vnp_TmnCode", "TESTTMN1");
		params.put("vnp_Amount", "15000000");
		params.put("vnp_CurrCode", "VND");
		params.put("vnp_TxnRef", "0f8fad5bd9cb469fa165708e9a2b3c4d");
		params.put("vnp_OrderInfo", "Thanh toan don hang 0f8fad5bd9cb469fa165708e9a2b3c4d");
		params.put("vnp_OrderType", "other");
		params.put("vnp_Locale", "vn");
		params.put("vnp_ReturnUrl", "http://localhost:8080/api/vnpay/return");
		params.put("vnp_IpAddr", "127.0.0.1");
		params.put("vnp_CreateDate", "20260929003000");
		params.put("vnp_ExpireDate", "20260929004500");
		params.put("vnp_BankCode", "");
		return params;
	}

}
