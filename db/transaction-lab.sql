CREATE TABLE IF NOT EXISTS account (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    balance DECIMAL(12, 2) NOT NULL,
    CONSTRAINT uq_account_name UNIQUE (name),
    CONSTRAINT chk_account_balance_nonnegative CHECK (balance >= 0)
) ENGINE=InnoDB;

INSERT INTO account (id, name, balance)
SELECT 1, 'A', 1000.00
WHERE NOT EXISTS (SELECT 1 FROM account WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM account WHERE name = 'A');

INSERT INTO account (id, name, balance)
SELECT 2, 'B', 1000.00
WHERE NOT EXISTS (SELECT 1 FROM account WHERE id = 2)
  AND NOT EXISTS (SELECT 1 FROM account WHERE name = 'B');
