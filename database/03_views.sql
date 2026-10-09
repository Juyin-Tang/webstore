USE webstore;

CREATE OR REPLACE VIEW v_order_summary AS
SELECT o.id                                         AS order_id,
       o.customer_id                                AS customer_id,
       CONCAT(c.first_name, ' ', c.last_name)       AS customer_name,
       c.email                                      AS customer_email,
       o.order_date                                 AS order_date,
       o.delivery_date                              AS delivery_date,
       o.status                                     AS status,
       COUNT(oi.product_id)                         AS line_count,
       COALESCE(SUM(oi.quantity), 0)                AS item_count,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_amount
FROM orders o
         JOIN customers c ON c.id = o.customer_id
         LEFT JOIN orderitems oi ON oi.order_id = o.id
GROUP BY o.id, o.customer_id, c.first_name, c.last_name, c.email,
         o.order_date, o.delivery_date, o.status;

CREATE OR REPLACE VIEW v_product_sales AS
SELECT p.id                                          AS product_id,
       p.name                                        AS product_name,
       pc.name                                       AS category_name,
       COALESCE(SUM(oi.quantity), 0)                 AS units_sold,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue
FROM products p
         LEFT JOIN productcategories pc ON pc.id = p.category_id
         LEFT JOIN (orderitems oi
    JOIN orders o ON o.id = oi.order_id AND o.status <> 'CANCELLED')
                   ON oi.product_id = p.id
GROUP BY p.id, p.name, pc.name;

CREATE USER IF NOT EXISTS 'webstore_report'@'localhost'
    IDENTIFIED BY 'Webstore_Report_2026!';
GRANT SELECT ON webstore.v_order_summary TO 'webstore_report'@'localhost';
GRANT SELECT ON webstore.v_product_sales TO 'webstore_report'@'localhost';