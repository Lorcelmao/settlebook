-- Merchant orders awaiting payment.
-- Money is stored as whole VND in bigint: VND has no minor unit, and any gateway scaling
-- (VNPay sends amount x 100) is applied only at the gateway adapter boundary.
CREATE TABLE orders (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    amount_vnd  bigint       NOT NULL CONSTRAINT orders_amount_positive CHECK (amount_vnd > 0),
    description varchar(255) NOT NULL CONSTRAINT orders_description_not_blank CHECK (btrim(description) <> ''),
    status      varchar(32)  NOT NULL DEFAULT 'AWAITING_PAYMENT'
                             CONSTRAINT orders_status_known
                             CHECK (status IN ('AWAITING_PAYMENT', 'PAID', 'EXPIRED', 'CANCELLED')),
    created_at  timestamptz  NOT NULL,
    expires_at  timestamptz  NOT NULL,
    CONSTRAINT orders_expiry_after_creation CHECK (expires_at > created_at)
);
