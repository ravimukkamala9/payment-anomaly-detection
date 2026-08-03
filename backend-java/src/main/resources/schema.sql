-- The generated payment dataset lives here instead of a Java in-memory list.
-- One row per (week, day, hour, cell, bin, acquirer) -- an hourly aggregate,
-- not a raw transaction. See PaymentDataGenerator for how rows are produced
-- and PaymentDb for the two-query pattern every stage/head runs against it.
DROP TABLE IF EXISTS payment_declines;

CREATE TABLE payment_declines (
    week            INT NOT NULL,
    day_of_week     INT NOT NULL,
    hour_of_day     INT NOT NULL,
    network         VARCHAR(20) NOT NULL,
    geography       VARCHAR(20) NOT NULL,
    entry_mode      VARCHAR(20) NOT NULL,
    purchase_type   VARCHAR(20) NOT NULL,
    auth_type       VARCHAR(20) NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    decline_code    VARCHAR(30) NOT NULL,
    bin             VARCHAR(10) NOT NULL,
    acquirer        VARCHAR(20) NOT NULL,
    total_count     INT NOT NULL,
    decline_count   INT NOT NULL,
    decline_rate    DOUBLE NOT NULL
);

-- Every stage/head query filters on (week, day_of_week, hour) first, then
-- groups by some subset of the dimension columns -- this index covers the
-- filter so the GROUP BY only ever scans the matching slice, not the table.
CREATE INDEX idx_payment_window ON payment_declines (day_of_week, hour_of_day, week);
