USE webstore;

SET SESSION system_versioning_alter_history = KEEP;

ALTER TABLE products ADD SYSTEM VERSIONING;

ALTER TABLE products
    MODIFY stock_quantity INT(11) NOT NULL DEFAULT 0 WITHOUT SYSTEM VERSIONING;