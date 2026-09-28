package dev.settlebook.payment;

import java.util.UUID;

/**
 * A gateway reports a successful charge for an order that is no longer awaiting payment: the order was
 * already paid by another attempt (a duplicate charge: the buyer really paid twice), or it expired or
 * was cancelled first (a late payment).
 *
 * <p>This is not a duplicate callback. The money is real and must be recorded as held for the buyer and
 * opened as a case, which the correctness core adds. Until then the result is rejected, the transaction
 * rolls back, and the gateway keeps retrying, so the charge is never silently dropped or booked as a
 * second sale.
 */
public class ChargeOnUnpayableOrderException extends RuntimeException {

	public ChargeOnUnpayableOrderException(UUID orderId, String txnRef) {
		super("Successful charge " + txnRef + " for order " + orderId + ", which is no longer awaiting payment");
	}

}
