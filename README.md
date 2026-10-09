# Webstore – REST API back-end

A back-end for a small web shop, built on the given `webstore` MariaDB database
(9 tables, about 2 million rows of test data).

Technologies: Java 17, Spring Boot 4.1 (Spring Web, Spring Data JPA / Hibernate 7, JDBC, Validation),
MariaDB 12, Postman.

The API serves two perspectives:

* **Shop / customer** – browse and search products, see the best sellers, register and update
  a customer account, place an order, see the customer's orders and their totals.
* **Administration** – manage categories and products, follow a product's price history,
  list orders by status, ship or cancel orders.

### Requirements and where they are covered

| Requirement | Solution | README |
|---|---|---|
| CRUD for the main resources | categories, products, customers, orders (23 endpoints) | [5](#5-api-endpoints) |
| Endpoints that combine tables | order detail (5 tables, one query), customer detail (customer + addresses + view), products with category and supplier names | [5.2](#52-products), [5.3](#53-customers), [5.4](#54-orders) |
| Views | `v_order_summary`, `v_product_sales` + a reporting user that can read only the views | [6.3](#63-views--03_viewssql) |
| Triggers | stock reservation, stock return, order status rules | [6.4](#64-triggers--04_triggerssql) |
| Indexes | 5 indexes, measured with `EXPLAIN` before and after | [6.2](#62-indexes--02_constraints_indexessql) |
| Transactions | an order and all its lines in one transaction, rollback on insufficient stock | [6.5](#65-transactions) |
| Temporal data | system-versioned `products` table, price history, `AS OF` query | [6.6](#66-temporal-data--05_temporalsql) |
| Security | least-privilege users, password in an environment variable, validation, parameterised queries | [7](#7-security) |

## Contents

1. [How to run](#1-how-to-run)
2. [Project structure](#2-project-structure)
3. [Data model](#3-data-model)
4. [API conventions](#4-api-conventions)
5. [API endpoints](#5-api-endpoints)
6. [Database solutions](#6-database-solutions)
7. [Security](#7-security)
8. [Testing](#8-testing)
9. [Limitations](#9-limitations)

---

## 1. How to run

**Database** – run as the administrator (root), in this order:

```bash
mariadb -u root -p -e "CREATE DATABASE webstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mariadb -u root -p webstore < schema_populated_dump.sql      # given schema + test data
mariadb -u root -p < database/01_security.sql                # application user
mariadb -u root -p < database/02_constraints_indexes.sql     # CHECK constraints + indexes
mariadb -u root -p < database/03_views.sql                   # views + reporting user
mariadb -u root -p < database/04_triggers.sql                # triggers
mariadb -u root -p < database/05_temporal.sql                # system versioning
```

**Application** – the database password is not stored in the code. Set it as an environment
variable (IntelliJ: *Run → Edit Configurations → Environment variables*):

```
DB_PASSWORD=Webstore_App_2026!
```

Then run `WebstoreApplication`. The API is at `http://localhost:8080`.

---

## 2. Project structure

```
database/                 SQL scripts: security, constraints + indexes, views, triggers, temporal table
src/main/java/com/example/webstore/
  entity/                 JPA entities (one per table)
  repository/             Spring Data JPA repositories + ReportQueries (SQL on views / temporal table)
  dto/                    request and response records
  service/                business logic and transactions
  controller/             REST controllers
  exception/              ApiException + global error handler
src/main/resources/application.yaml
webstore.postman_collection.json   Postman tests
```

Layers: **controller** (HTTP, validation) → **service** (transactions, rules) →
**repository** (database). Entities are never returned directly: every response is a DTO
(Java `record`), so there are no JSON loops or lazy-loading errors.

---

## 3. Data model

The tables come from the given dump (**database first**). Hibernate only checks that the
entities match the tables (`ddl-auto: validate`).

| Table | Entity | Relationships |
|---|---|---|
| `productcategories` | `ProductCategory` | 1:M → products |
| `suppliers` | `Supplier` | 1:M → products |
| `products` | `Product` | M:1 → category, M:1 → supplier |
| `customers` | `Customer` | 1:M → addresses, 1:M → orders |
| `customeraddresses` | `CustomerAddress` | M:1 → customer |
| `orders` | `CustomerOrder` | M:1 → customer, M:1 → shipping address, 1:M → order lines |
| `orderitems` | `OrderItem` | M:1 → order, M:1 → product |

* `orderitems` is the link table of the M:N relationship orders ↔ products, but it also has
  `quantity` and `unit_price`. `@ManyToMany` cannot store extra columns, so `OrderItem` is its
  own entity with a **composite key** (`@EmbeddedId OrderItemId(orderId, productId)`) and two
  `@ManyToOne` relations with `@MapsId`.
* The order class is called `CustomerOrder` because `ORDER` is a reserved word in SQL.
* All `@ManyToOne` relations are `LAZY`. The data that is needed is loaded on purpose with
  `JOIN FETCH` (order detail) or `@EntityGraph` (product and order lists) – no N+1 queries.

---

## 4. API conventions

* Bodies are JSON; dates are ISO-8601 (`2024-04-03T17:51:08`).
* **Status codes:** `200` OK, `201` Created (with `Location` header), `204` No Content,
  `400` invalid input, `404` not found, `409` conflict (a business rule or a database
  constraint prevents the operation), `500` unexpected error.
* **Paging** for the big lists: `?page=0&size=20` (size 1–100). Response:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 1000, "totalPages": 50 }
```

* **Errors** always have the same format (no stack traces, no SQL):

```json
{ "timestamp": "2026-10-09T21:34:10.25687", "status": 409, "error": "Conflict",
  "message": "Insufficient stock for product 526", "path": "/api/orders" }
```

---

## 5. API endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/categories` | List categories |
| GET | `/api/categories/{id}` | One category |
| POST | `/api/categories` | Create category |
| PUT | `/api/categories/{id}` | Update category |
| DELETE | `/api/categories/{id}` | Delete category (409 if it has products) |
| GET | `/api/products?q=&categoryId=&page=&size=` | Search products (paged) |
| GET | `/api/products/{id}` | One product with category and supplier names |
| POST | `/api/products` | Create product |
| PUT | `/api/products/{id}` | Update product (price change → new history version) |
| DELETE | `/api/products/{id}` | Delete product (409 if it appears in orders) |
| GET | `/api/products/{id}/history` | Price history (temporal table) |
| GET | `/api/products/top-sellers?limit=` | Best-selling products (view) |
| GET | `/api/customers?page=&size=` | List customers (paged) |
| GET | `/api/customers/{id}` | Customer + addresses + order statistics |
| GET | `/api/customers/{id}/orders` | Orders of the customer with totals (view) |
| POST | `/api/customers` | Create customer (409 if the e-mail is taken) |
| PUT | `/api/customers/{id}` | Update customer |
| DELETE | `/api/customers/{id}` | Delete customer (409 if the customer has orders) |
| GET | `/api/orders?status=&page=&size=` | List orders, newest first (paged) |
| GET | `/api/orders/{id}` | Order with customer, address, lines and total |
| POST | `/api/orders` | Place an order (transaction + stock trigger) |
| PATCH | `/api/orders/{id}/status` | Ship or cancel an order (triggers) |
| DELETE | `/api/orders/{id}` | Delete a NEW or CANCELLED order (stock is returned) |

### 5.1 Categories

**GET `/api/categories`** → `200`
```json
[ { "id": 1, "name": "Elektroniikka", "description": "Sähkölaitteet, komponentit ja elektroniikkatuotteet" }, ... ]
```

**GET `/api/categories/{id}`** → `200` one category, `404` if not found.

**POST `/api/categories`**, **PUT `/api/categories/{id}`** – request:
```json
{ "name": "Lemmikit", "description": "Lemmikkitarvikkeet" }
```
`name` is required (max 100). → `201` / `200` with the category (including `id`).

**DELETE `/api/categories/{id}`** → `204`, or `409` if the category still has products.

### 5.2 Products

**GET `/api/products`** – parameters: `q` (part of the name), `categoryId`, `page`, `size`.
Example `/api/products?categoryId=2` → `200` (shortened)
```json
{
  "content": [
    { "id": 1, "name": "Super Bug 360", "description": "Her fall move current him.", "price": 22.22,
      "stockQuantity": 136, "categoryId": 2, "categoryName": "Kodin tarvikkeet",
      "supplierId": 35, "supplierName": "Alandia Pharma" },
    ...
  ],
  "page": 0, "size": 20, "totalElements": 103, "totalPages": 6
}
```

**GET `/api/products/{id}`** → `200` one product (fields as above), `404` if not found.

**POST `/api/products`**, **PUT `/api/products/{id}`** – request:
```json
{ "name": "Test Lamp", "description": "Desk lamp", "price": 9.90, "stockQuantity": 10, "categoryId": 1, "supplierId": 1 }
```
`name`, `price` (≥ 0, 2 decimals) and `stockQuantity` (≥ 0) are required; `categoryId` and
`supplierId` are optional, an unknown id gives `400`. → `201` / `200` with the product.

**DELETE `/api/products/{id}`** → `204`, or `409` if the product appears in an order.

**GET `/api/products/{id}/history`** – every version from the system-versioned table, oldest first:
```json
[
  { "name": "Test Lamp", "price": 9.90,  "validFrom": "2026-10-09T21:24:36.347874", "validTo": "2026-10-09T21:24:48.681075" },
  { "name": "Test Lamp", "price": 12.50, "validFrom": "2026-10-09T21:24:48.681075", "validTo": "2026-10-09T21:24:54.99905" },
  { "name": "Test Lamp", "price": 11.00, "validFrom": "2026-10-09T21:24:54.99905", "validTo": null }
]
```
`validTo = null` is the current version. Works also after the product is deleted. `404` if the id never existed.

**GET `/api/products/top-sellers?limit=5`** – from the view `v_product_sales` (`limit` 1–100):
```json
[ { "productId": 626, "productName": "Nimble Snack 972", "categoryName": "Puutarha & piha", "unitsSold": 2472, "revenue": 618914.64 }, ... ]
```

### 5.3 Customers

**GET `/api/customers?page=0&size=20`** → paged list of
```json
{ "id": 1, "firstName": "Dustin", "lastName": "Carey", "email": "vsmith1@example.com", "phone": "001-789-824-7188x591" }
```

**GET `/api/customers/{id}`** – combines three sources: the customer (JPA), the addresses (JPA)
and statistics from the view `v_order_summary` (cancelled orders are not counted):
```json
{ "id": 1, "firstName": "Dustin", "lastName": "Carey", "email": "vsmith1@example.com",
  "phone": "001-789-824-7188x591",
  "addresses": [ { "id": 1, "streetAddress": "39650 Harrington Plains Suite 474", "postalCode": "95942",
                   "city": "Larryview", "country": "Guadeloupe" } ],
  "orderCount": 3, "totalSpent": 9300.09 }
```

**GET `/api/customers/{id}/orders`** – the customer's orders, newest first (view `v_order_summary`):
```json
[ { "orderId": 200001, "orderDate": "2026-10-09T21:34:04", "deliveryDate": "2026-10-09T21:34:19", "status": "SHIPPED", "itemCount": 3, "totalAmount": 780.88 },
  { "orderId": 181269, "orderDate": "2024-05-02T00:20:53", "deliveryDate": "2024-05-08T04:18:07", "status": "NEW", "itemCount": 14, "totalAmount": 7023.97 }, ... ]
```
(order 200001 was placed through the API during testing; the numbers grow with every test order)

**POST `/api/customers`**, **PUT `/api/customers/{id}`** – request:
```json
{ "firstName": "Maija", "lastName": "Meikäläinen", "email": "maija@example.fi", "phone": "0401112222" }
```
All except `phone` are required, `email` must be valid → `400`. `409` if another customer
already has the e-mail (also guaranteed by a UNIQUE index). → `201` / `200`.

**DELETE `/api/customers/{id}`** → `204`, or `409` if the customer has orders.

### 5.4 Orders

**GET `/api/orders?status=NEW&page=0&size=20`** – newest first; `status` is optional
(`NEW`, `SHIPPED`, `CANCELLED`, otherwise `400`). → paged list of
```json
{ "orderId": 1, "customerId": 51914, "customerName": "Gabriel Parker", "orderDate": "2024-04-03T17:51:08", "status": "CANCELLED" }
```

**GET `/api/orders/{id}`** – the whole order in one SQL query (`JOIN FETCH` over 5 tables):
```json
{ "orderId": 1, "orderDate": "2024-04-03T17:51:08", "deliveryDate": "2024-04-06T16:16:43", "status": "CANCELLED",
  "customer": { "id": 51914, "firstName": "Gabriel", "lastName": "Parker", "email": "walshdaniel51914@example.com", "phone": "915-295-4987x8639" },
  "shippingAddress": { "id": 51914, "streetAddress": "931 Sheryl Square Apt. 340", "postalCode": "39626", "city": "Johnfort", "country": "Turkmenistan" },
  "items": [
    { "productId": 426, "productName": "Nova Blade 383", "quantity": 2, "unitPrice": 736.44, "lineTotal": 1472.88 },
    { "productId": 551, "productName": "Zippy Carrier 351", "quantity": 3, "unitPrice": 611.67, "lineTotal": 1835.01 }
  ],
  "totalAmount": 3307.89 }
```

**POST `/api/orders`** – place an order:
```json
{ "customerId": 1, "shippingAddressId": 1,
  "items": [ { "productId": 1, "quantity": 2 }, { "productId": 426, "quantity": 1 } ] }
```
* `items` must not be empty; `quantity` 1–100; each product only once.
* `shippingAddressId` is optional but must belong to the customer (`400` otherwise).
* The current product price is copied to `unit_price`.
* Everything happens in **one transaction**. If any product does not have enough stock the
  answer is `409` and **nothing** is saved (see 6.5).

→ `201` with the order (same format as GET `/api/orders/{id}`), status `NEW`.

**PATCH `/api/orders/{id}/status`** – `{ "status": "SHIPPED" }` or `{ "status": "CANCELLED" }`.
Allowed (checked by a trigger): `NEW → SHIPPED`, `NEW → CANCELLED`; others → `409`.
Shipping fills in `deliveryDate`, cancelling returns the stock. → `200` with the order.

**DELETE `/api/orders/{id}`** → `204`. `409` for a `SHIPPED` order. When a `NEW` order is
deleted the stock is returned by a trigger.

---

## 6. Database solutions

All scripts are in the folder [`database/`](database).

### 6.1 Constraints – `02_constraints_indexes.sql`

| Constraint | Rule |
|---|---|
| `chk_products_price_nonnegative` | `price >= 0` |
| `chk_products_stock_nonnegative` | `stock_quantity >= 0` |
| `chk_orderitems_quantity_positive` | `quantity > 0` |
| `chk_orderitems_price_nonnegative` | `unit_price >= 0` |
| `chk_orders_status` | `status IN ('NEW','SHIPPED','CANCELLED')` |

The database refuses impossible values even if a client has a bug.

### 6.2 Indexes – `02_constraints_indexes.sql`

The foreign-key columns already had indexes. These indexes support the API queries:

| Index | Columns | Used by |
|---|---|---|
| `ux_customers_email` (UNIQUE) | `customers(email)` | e-mail check in POST/PUT `/api/customers`; also guarantees unique e-mails |
| `idx_orders_order_date` | `orders(order_date)` | `GET /api/orders` newest first |
| `idx_orders_status_date` | `orders(status, order_date)` | `GET /api/orders?status=` – filter and sort with one index |
| `idx_orders_customer_date` | `orders(customer_id, order_date)` | `GET /api/customers/{id}/orders` |
| `idx_orderitems_product_cover` | `orderitems(product_id, quantity, unit_price)` | view `v_product_sales` – a *covering* index: the sums are read from the index only |

Measured with `EXPLAIN` before and after:

| Query | Before | After |
|---|---|---|
| `SELECT * FROM customers WHERE email = 'vsmith1@example.com'` | `type=ALL`, no key (full table scan) | `type=const`, key `ux_customers_email` |
| `SELECT id FROM orders WHERE status='NEW' ORDER BY order_date DESC LIMIT 20` | `type=ALL`, no key (full table scan) | `type=ref`, key `idx_orders_status_date` |
| `SELECT id FROM orders WHERE customer_id = 1 ORDER BY order_date DESC` | `type=ref`, key `fk_order_customer` (rows must still be sorted) | `type=ref`, key `idx_orders_customer_date` (already in date order) |

### 6.3 Views – `03_views.sql`

| View | Content | Used by |
|---|---|---|
| `v_order_summary` | one row per order: customer, dates, status, number of items, **total amount** | `GET /api/customers/{id}/orders`, statistics in `GET /api/customers/{id}` |
| `v_product_sales` | units sold and revenue per product (cancelled orders excluded) | `GET /api/products/top-sellers` |

The joins and sums are written once in the database; the Java code (`ReportQueries`) only
runs `SELECT ... FROM view WHERE ...`.

**Views as a security layer:** the user `webstore_report` can only `SELECT` from these two
views – it cannot read the base tables (addresses, phone numbers) or change anything.

### 6.4 Triggers – `04_triggers.sql`

| Trigger | When | What it does |
|---|---|---|
| `trg_orderitems_before_insert` | before an order line is inserted | takes the quantity from the stock with one `UPDATE ... WHERE stock_quantity >= quantity`; if not enough stock: `SIGNAL SQLSTATE '45000'` "Insufficient stock for product X" |
| `trg_orderitems_after_delete` | after an order line is deleted | returns the stock (not for cancelled orders – they already got it back) |
| `trg_orders_before_update` | before an order is updated | allows only `NEW → SHIPPED` and `NEW → CANCELLED`; sets `delivery_date` when shipped |
| `trg_orders_after_update` | after an order is updated | when an order is cancelled, returns the stock of all its lines |

The rules hold for every client, not only this API. `GlobalExceptionHandler` turns a trigger
error (SQLSTATE `45000`) into `409 Conflict` with the trigger's message.

### 6.5 Transactions

* Every service class is `@Transactional`; read methods use `@Transactional(readOnly = true)`.
* **Placing an order** (`OrderService.create`) inserts the order and all its lines in
  **one transaction**, and the stock trigger runs for every line. If the second line fails,
  the whole transaction is rolled back: the order, the first line **and** the stock already
  taken for it. Test: order product 1 together with product 526 (stock 0) → `409`, and
  product 1's stock is unchanged.
* The stock check and the decrement are one `UPDATE` statement, so two orders at the same
  time cannot sell the same last item.
* `saveAndFlush` sends the SQL inside the method, so trigger errors happen inside the
  transaction and are rolled back cleanly.

### 6.6 Temporal data – `05_temporal.sql`

`products` is a **system-versioned table**: MariaDB keeps every old version of a row with
its validity period (`ROW_START`, `ROW_END`).

* `stock_quantity` is `WITHOUT SYSTEM VERSIONING`: stock changes with every order and is not
  useful history. Changing only the stock creates no version; changing the price does.
* `GET /api/products/{id}/history` reads `FROM products FOR SYSTEM_TIME ALL`.
* A product at a given moment: `SELECT * FROM products FOR SYSTEM_TIME AS OF TIMESTAMP '2026-10-09 21:24:36.347874' WHERE id = 1003;` (returned the first version, price 9.90, after the product had been deleted)
* Deleted products keep their history.

---

## 7. Security

* **Least-privilege database user** (`01_security.sql`): the application connects as
  `webstore_app` with only `SELECT, INSERT, UPDATE, DELETE` on `webstore.*` – it cannot change
  the schema, grant rights, use other databases or delete the price history.
* **Reporting user** `webstore_report` can read only the two views.
* **No password in the code**: `password: ${DB_PASSWORD}` comes from an environment variable.
* **No SQL injection**: all JPQL and SQL queries use parameters.
* **Input validation** (`@NotBlank`, `@Email`, `@Size`, `@Min`, ...) → `400`; page size is
  limited to 100.
* **Integrity in the database**: foreign keys, UNIQUE e-mail, CHECK constraints and triggers.
* **No internal details in errors**: stack traces and SQL are only written to the server log.

---

## 8. Testing

All endpoints were tested with Postman (`webstore.postman_collection.json`, 51 requests).
The whole collection was run with the Postman Collection Runner: every request returned the
expected status code (the expected code is written in the request name, e.g. `(409)`).

| Scenario | Expected |
|---|---|
| Create / update / delete category, product, customer | 201 / 200 / 204 |
| Delete a category that has products | 409 |
| Create a customer with an existing e-mail | 409 |
| Invalid body (empty name, negative price, bad e-mail) | 400 |
| Place an order | 201, stock decreases |
| Order with a product that has no stock | 409, nothing saved, stock unchanged |
| Ship an order | 200, `deliveryDate` filled in |
| Cancel a shipped order | 409 (trigger) |
| Cancel a new order | 200, stock returned |
| Change a product price twice, read `/history` | 3 versions |

---

## 9. Limitations

* There is no login or user authentication; the API is meant to run locally.
* Suppliers, supplier addresses and the `contacts` table are not exposed through the API.
  Customer addresses can be read (customer detail, order detail) but not edited through the API.
* Test orders made with Postman really change the data (stock, order count), so some numbers
  in the examples above grow when the tests are run again.
