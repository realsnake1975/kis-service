package dev.kis.openapi.domestic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CashOrderRequestTest {
    @Test
    void acceptsValidLimitOrder() {
        assertDoesNotThrow(() -> new CashOrderRequest("005930", CashOrderRequest.Side.BUY,
                CashOrderRequest.OrderType.LIMIT, 2, new BigDecimal("70000")));
    }

    @Test
    void rejectsInvalidSymbolAndQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new CashOrderRequest("5930",
                CashOrderRequest.Side.BUY, CashOrderRequest.OrderType.LIMIT, 1, new BigDecimal("70000")));
        assertThrows(IllegalArgumentException.class, () -> new CashOrderRequest("005930",
                CashOrderRequest.Side.BUY, CashOrderRequest.OrderType.LIMIT, 0, new BigDecimal("70000")));
    }

    @Test
    void requiresPositivePriceForLimitOrder() {
        assertThrows(IllegalArgumentException.class, () -> new CashOrderRequest("005930",
                CashOrderRequest.Side.BUY, CashOrderRequest.OrderType.LIMIT, 1, BigDecimal.ZERO));
    }
}
