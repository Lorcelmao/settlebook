-- One row per redirect of a buyer to a payment gateway. An order can have several attempts
-- (e.g. the buyer abandons the first one and tries again).
CREATE TABLE payment_attempts (
    id                     uuid         PRIMARY KEY,
    order_id               uuid         NOT NULL REFERENCES orders (id),
    gateway                varchar(16)  NOT NULL CONSTRAINT payment_attempts_gateway_known CHECK (gateway IN ('VNPAY')),
    -- Our reference sent to the gateway (vnp_TxnRef). Unique per gateway, which is stricter than
    -- VNPay's own rule (unique per day).
    txn_ref                varchar(100) NOT NULL,
    amount_vnd             bigint       NOT NULL CONSTRAINT payment_attempts_amount_positive CHECK (amount_vnd > 0),
    status                 varchar(32)  NOT NULL DEFAULT 'PENDING'
                                        CONSTRAINT payment_attempts_status_known
                                        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    -- Result fields reported by the gateway; null until a callback is applied.
    gateway_transaction_no varchar(64),
    gateway_response_code  varchar(8),
    gateway_bank_code      varchar(32),
    gateway_paid_at        timestamptz,
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    CONSTRAINT payment_attempts_gateway_txn_ref_unique UNIQUE (gateway, txn_ref)
);

CREATE INDEX payment_attempts_order_id_idx ON payment_attempts (order_id);
