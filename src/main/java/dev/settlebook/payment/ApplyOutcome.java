package dev.settlebook.payment;

/** What happened when a gateway result was applied. Gateway adapters map this to their reply codes. */
public enum ApplyOutcome {

	/** The result changed the attempt (and, on success, paid the order). */
	APPLIED,

	/**
	 * The attempt already has a final result, so nothing changed. This is what a duplicate callback
	 * (a replay of the same gateway event) produces.
	 */
	ALREADY_APPLIED,

	/** No attempt with this gateway reference exists. */
	ATTEMPT_NOT_FOUND,

	/** The reported amount differs from the attempt's amount; nothing changed. */
	AMOUNT_MISMATCH

}
