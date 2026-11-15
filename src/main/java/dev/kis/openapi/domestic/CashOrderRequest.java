package dev.kis.openapi.domestic;

import java.math.BigDecimal;
import java.util.Objects;

public record CashOrderRequest(String symbol, Side side, OrderType orderType, long quantity, BigDecimal price) {
    public CashOrderRequest {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(price, "price");
        if (!symbol.matches("\\d{6}")) throw new IllegalArgumentException("symbol must be a six-digit KRX code");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than zero");
        if (price.signum() < 0) throw new IllegalArgumentException("price cannot be negative");
        if (orderType == OrderType.LIMIT && price.signum() <= 0) {
            throw new IllegalArgumentException("limit orders require a positive price");
        }
    }

    public enum Side { BUY, SELL }

    /** KIS order codes: 00 limit, 01 market. */
    public enum OrderType {
        LIMIT("00"), MARKET("01");
        private final String kisCode;
        OrderType(String kisCode) { this.kisCode = kisCode; }
        public String kisCode() { return kisCode; }
    }
}
