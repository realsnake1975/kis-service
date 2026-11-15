package dev.kis.openapi.domestic;

import java.math.BigDecimal;
import java.util.Objects;

/** Request for amending or cancelling an unfilled domestic cash order. */
public record OrderAmendCancelRequest(
        String orderBranchNo,
        String originalOrderNo,
        CashOrderRequest.OrderType orderType,
        Action action,
        long quantity,
        BigDecimal unitPrice,
        boolean allRemaining,
        Exchange exchange) {

    public OrderAmendCancelRequest {
        requireText(orderBranchNo, "orderBranchNo");
        requireText(originalOrderNo, "originalOrderNo");
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(exchange, "exchange");
        if (quantity < 0 || (!allRemaining && quantity == 0)) {
            throw new IllegalArgumentException("quantity must be positive unless allRemaining is true");
        }
        if (unitPrice.signum() < 0) throw new IllegalArgumentException("unitPrice cannot be negative");
        if (action == Action.REVISE && orderType == CashOrderRequest.OrderType.LIMIT && unitPrice.signum() == 0) {
            throw new IllegalArgumentException("limit revisions require a positive unitPrice");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    public enum Action {
        REVISE("01"), CANCEL("02");
        private final String code;
        Action(String code) { this.code = code; }
        public String code() { return code; }
    }

    public enum Exchange {
        KRX, NXT, SOR
    }
}
