package dev.settlebook.gateway.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.settlebook.payment.GatewayResult;

class VnpayCallbacksTest {

	@Test
	void successNeedsBothResponseCodeAndTransactionStatusZero() {
		assertThat(VnpayCallbacks.toResult(callback("00", "00")).successful()).isTrue();
		assertThat(VnpayCallbacks.toResult(callback("00", "02")).successful()).isFalse();
		assertThat(VnpayCallbacks.toResult(callback("24", "02")).successful()).isFalse();
	}

	@Test
	void unscalesAmountAndParsesPayDateInGmtPlus7() {
		GatewayResult result = VnpayCallbacks.toResult(callback("00", "00"));

		assertThat(result.amountVnd()).isEqualTo(150_000);
		assertThat(result.paidAt()).isEqualTo(Instant.parse("2026-09-28T17:31:05Z"));
		assertThat(result.transactionNo()).isEqualTo("14512345");
	}

	@Test
	void rejectsFractionalAmountAndMissingFields() {
		Map<String, String> fractional = callback("00", "00");
		fractional.put("vnp_Amount", "15000050");
		assertThatThrownBy(() -> VnpayCallbacks.toResult(fractional)).isInstanceOf(IllegalArgumentException.class);

		Map<String, String> noStatus = callback("00", "00");
		noStatus.remove("vnp_TransactionStatus");
		assertThatThrownBy(() -> VnpayCallbacks.toResult(noStatus)).isInstanceOf(IllegalArgumentException.class);
	}

	private static Map<String, String> callback(String responseCode, String transactionStatus) {
		Map<String, String> params = new HashMap<>();
		params.put("vnp_TxnRef", "0f8fad5bd9cb469fa165708e9a2b3c4d");
		params.put("vnp_Amount", "15000000");
		params.put("vnp_ResponseCode", responseCode);
		params.put("vnp_TransactionStatus", transactionStatus);
		params.put("vnp_TransactionNo", "14512345");
		params.put("vnp_BankCode", "NCB");
		params.put("vnp_PayDate", "20260929003105");
		return params;
	}

}
