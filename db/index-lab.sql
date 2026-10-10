-- Stage 6 baseline only: create and seed order_lab without secondary indexes.
-- Safe to re-run: only missing IDs are inserted; existing rows are never updated or deleted.
CREATE TABLE IF NOT EXISTS order_lab (
    id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO order_lab (id, user_id, status, created_at, amount)
SELECT seed_rows.id,
       MOD(seed_rows.id * 7919 + 17, 1000) + 1,
       CASE MOD(seed_rows.id - 1, 10)
           WHEN 0 THEN 'PAID'
           WHEN 1 THEN 'PAID'
           WHEN 2 THEN 'PAID'
           WHEN 3 THEN 'PAID'
           WHEN 4 THEN 'PAID'
           WHEN 5 THEN 'PAID'
           WHEN 6 THEN 'PENDING'
           WHEN 7 THEN 'PENDING'
           WHEN 8 THEN 'CANCELLED'
           WHEN 9 THEN 'CANCELLED'
       END,
       DATE_ADD(
           DATE_ADD('2025-07-01 00:00:00', INTERVAL MOD((seed_rows.id - 1) * 37, 365) DAY),
           INTERVAL MOD(seed_rows.id * 17, 1440) MINUTE
       ),
       CAST(10.00 + MOD(seed_rows.id * 7919, 99001) / 100.00 AS DECIMAL(12, 2))
FROM (
    SELECT block_no.n * 10000 + d4.n * 1000 + d3.n * 100 + d2.n * 10 + d1.n + 1 AS id
    FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) AS block_no
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS d4
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS d3
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS d2
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS d1
) AS seed_rows
WHERE NOT EXISTS (
    SELECT 1
    FROM order_lab AS existing
    WHERE existing.id = seed_rows.id
);

-- Baseline query (run separately to inspect its result rows):
-- SELECT id, user_id, status, created_at, amount
-- FROM order_lab
-- WHERE status = 'PAID'
--   AND created_at >= '2026-01-01'
--   AND created_at < '2026-02-01';

EXPLAIN FORMAT=TRADITIONAL
SELECT id, user_id, status, created_at, amount
FROM order_lab
WHERE status = 'PAID'
  AND created_at >= '2026-01-01'
  AND created_at < '2026-02-01';

-- A later learning step may add a composite index after this baseline is recorded:
-- CREATE INDEX idx_order_lab_status_created_at ON order_lab (status, created_at);
