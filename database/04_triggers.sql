USE webstore;

DELIMITER //

CREATE OR REPLACE TRIGGER trg_orderitems_before_insert
    BEFORE INSERT ON orderitems
    FOR EACH ROW
BEGIN
    DECLARE msg VARCHAR(255);

UPDATE products
SET stock_quantity = stock_quantity - NEW.quantity
WHERE id = NEW.product_id
  AND stock_quantity >= NEW.quantity;

IF ROW_COUNT() = 0 THEN
        SET msg = CONCAT('Insufficient stock for product ', NEW.product_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = msg;
END IF;
END//

CREATE OR REPLACE TRIGGER trg_orderitems_after_delete
    AFTER DELETE ON orderitems
    FOR EACH ROW
BEGIN
    IF COALESCE((SELECT status FROM orders WHERE id = OLD.order_id), 'NEW') <> 'CANCELLED' THEN
UPDATE products
SET stock_quantity = stock_quantity + OLD.quantity
WHERE id = OLD.product_id;
END IF;
END//

CREATE OR REPLACE TRIGGER trg_orders_before_update
    BEFORE UPDATE ON orders
                         FOR EACH ROW
BEGIN
    IF NEW.status IS NULL OR NEW.status NOT IN ('NEW', 'SHIPPED', 'CANCELLED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid order status';
END IF;

    IF OLD.status = 'CANCELLED' AND NEW.status <> 'CANCELLED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A cancelled order cannot be changed';
END IF;

    IF OLD.status = 'SHIPPED' AND NEW.status <> 'SHIPPED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A shipped order cannot be changed';
END IF;

    IF NEW.status = 'SHIPPED' AND OLD.status <> 'SHIPPED' AND NEW.delivery_date IS NULL THEN
        SET NEW.delivery_date = NOW();
END IF;
END//

CREATE OR REPLACE TRIGGER trg_orders_after_update
    AFTER UPDATE ON orders
                        FOR EACH ROW
BEGIN
    IF NEW.status = 'CANCELLED' AND OLD.status <> 'CANCELLED' THEN
UPDATE products p
    JOIN orderitems oi ON oi.product_id = p.id
    SET p.stock_quantity = p.stock_quantity + oi.quantity
WHERE oi.order_id = NEW.id;
END IF;
END//

DELIMITER ;

SHOW TRIGGERS FROM webstore;