CREATE TABLE shipment (
    id                  UUID                     NOT NULL PRIMARY KEY,
    tracking_number     VARCHAR(20)              NOT NULL,
    internal_reference  VARCHAR(64),
    sender_name         VARCHAR(120)             NOT NULL,
    recipient_name      VARCHAR(120)             NOT NULL,
    origin_city         VARCHAR(80)              NOT NULL,
    origin_country      VARCHAR(2)               NOT NULL,
    destination_city    VARCHAR(80)              NOT NULL,
    destination_country VARCHAR(2)               NOT NULL,
    service_level       VARCHAR(20)              NOT NULL,
    weight_grams        INTEGER                  NOT NULL,
    status              VARCHAR(20)              NOT NULL,
    failed_attempts     INTEGER                  NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    picked_up_at        TIMESTAMP WITH TIME ZONE,
    delivered_at        TIMESTAMP WITH TIME ZONE,
    last_event_at       TIMESTAMP WITH TIME ZONE,
    version             BIGINT                   NOT NULL,
    CONSTRAINT uq_shipment_tracking_number UNIQUE (tracking_number),
    CONSTRAINT ck_shipment_weight CHECK (weight_grams > 0)
);

CREATE INDEX ix_shipment_status_last_event ON shipment (status, last_event_at);

-- Append-only: rows are inserted, never updated or deleted by the application.
CREATE TABLE tracking_event (
    id              UUID                     NOT NULL PRIMARY KEY,
    shipment_id     UUID                     NOT NULL REFERENCES shipment (id),
    event_type      VARCHAR(20)              NOT NULL,
    status_after    VARCHAR(20)              NOT NULL,
    source          VARCHAR(20)              NOT NULL,
    description     VARCHAR(255),
    city            VARCHAR(80)              NOT NULL,
    country         VARCHAR(2)               NOT NULL,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(100),
    request_hash    VARCHAR(64),
    CONSTRAINT uq_tracking_event_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_tracking_event_shipment ON tracking_event (shipment_id, occurred_at);
