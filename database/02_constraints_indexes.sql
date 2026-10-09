USE webstore;

ALTER TABLE products
    ADD CONSTRAINT chk_products_price_nonnegative CHECK (price >= 0),
    ADD CONSTRAINT chk_products_stock_nonnegative CHECK (stock_quantity >= 0);

ALTER TABLE orderitems
    ADD CONSTRAINT chk_orderitems_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT chk_orderitems_price_nonnegative CHECK (unit_price >= 0);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status CHECK (status IN ('NEW', 'SHIPPED', 'CANCELLED'));

CREATE UNIQUE INDEX ux_customers_email ON customers (email);

CREATE INDEX idx_orders_order_date ON orders (order_date);

CREATE INDEX idx_orders_status_date ON orders (status, order_date);

CREATE INDEX idx_orders_customer_date ON orders (customer_id, order_date);

CREATE INDEX idx_orderitems_product_cover ON orderitems (product_id, quantity, unit_price);