-- Fail rather than silently rewrite any conflicting historical data.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD CONSTRAINT valid_booking_dates CHECK (check_out_date > check_in_date);
ALTER TABLE bookings ADD CONSTRAINT valid_booking_guests CHECK (guests_count >= 1);
ALTER TABLE bookings ADD CONSTRAINT valid_booking_price CHECK (total_price > 0);
ALTER TABLE bookings ADD CONSTRAINT different_booking_participants CHECK (tenant_id <> owner_id);
ALTER TABLE bookings ADD CONSTRAINT valid_booking_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED'));
-- Checkout is exclusive: an arrival on the previous checkout date is permitted.
ALTER TABLE bookings ADD CONSTRAINT no_overlapping_active_bookings
    EXCLUDE USING gist (listing_id public.gist_uuid_ops WITH =, daterange(check_in_date, check_out_date, '[)') WITH &&)
    WHERE (status IN ('PENDING', 'CONFIRMED'));

CREATE TABLE booking_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('created', 'confirmed', 'cancelled')),
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_booking_outbox_order ON booking_outbox(aggregate_id, id);
