package dev.kis.openapi.domestic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderAmendCancelRequestTest {
    @Test
    void acceptsFullRemainingCancellationWithZeroQuantity() {
        assertDoesNotThrow(() -> new OrderAmendCancelRequest("00123", "0000123456",
                CashOrderRequest.OrderType.LIMIT, OrderAmendCancelRequest.Action.CANCEL,
                0, BigDecimal.ZERO, true, OrderAmendCancelRequest.Exchange.KRX));
    }

    @Test
    void rejectsPartialOperationWithoutQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new OrderAmendCancelRequest("00123", "0000123456",
                CashOrderRequest.OrderType.LIMIT, OrderAmendCancelRequest.Action.CANCEL,
                0, BigDecimal.ZERO, false, OrderAmendCancelRequest.Exchange.KRX));
    }

    @Test
    void requiresPositivePriceForLimitRevision() {
        assertThrows(IllegalArgumentException.class, () -> new OrderAmendCancelRequest("00123", "0000123456",
                CashOrderRequest.OrderType.LIMIT, OrderAmendCancelRequest.Action.REVISE,
                1, BigDecimal.ZERO, false, OrderAmendCancelRequest.Exchange.KRX));
    }
}
