package com.example.webstore.repository;

import com.example.webstore.dto.CatalogDtos.ProductSalesResponse;
import com.example.webstore.dto.CatalogDtos.ProductVersionResponse;
import com.example.webstore.dto.OrderDtos.OrderSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ReportQueries {

    private final JdbcTemplate jdbc;

    public ReportQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OrderSummaryResponse> customerOrders(int customerId) {
        String sql = """
                SELECT order_id, order_date, delivery_date, status, item_count, total_amount
                FROM v_order_summary
                WHERE customer_id = ?
                ORDER BY order_date DESC
                """;
        return jdbc.query(sql, (rs, rowNum) -> new OrderSummaryResponse(
                rs.getInt("order_id"),
                rs.getObject("order_date", LocalDateTime.class),
                rs.getObject("delivery_date", LocalDateTime.class),
                rs.getString("status"),
                rs.getLong("item_count"),
                rs.getBigDecimal("total_amount")), customerId);
    }

    public List<ProductSalesResponse> topProducts(int limit) {
        String sql = """
                SELECT product_id, product_name, category_name, units_sold, revenue
                FROM v_product_sales
                ORDER BY units_sold DESC, product_id
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new ProductSalesResponse(
                rs.getInt("product_id"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                rs.getLong("units_sold"),
                rs.getBigDecimal("revenue")), limit);
    }

    public List<ProductVersionResponse> productHistory(int productId) {
        String sql = """
                SELECT name, price, ROW_START AS valid_from, ROW_END AS valid_to
                FROM products FOR SYSTEM_TIME ALL
                WHERE id = ?
                ORDER BY ROW_START
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            LocalDateTime validTo = rs.getObject("valid_to", LocalDateTime.class);
            if (validTo != null && validTo.getYear() >= 2038) {
                validTo = null;
            }
            return new ProductVersionResponse(rs.getString("name"), rs.getBigDecimal("price"),
                    rs.getObject("valid_from", LocalDateTime.class), validTo);
        }, productId);
    }
}