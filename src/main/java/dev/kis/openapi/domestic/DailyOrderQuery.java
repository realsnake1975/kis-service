package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiPage;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Filters and continuation keys for KIS domestic daily order/fill history. */
public record DailyOrderQuery(
        LocalDate startDate,
        LocalDate endDate,
        PeriodRange periodRange,
        Side side,
        FillStatus fillStatus,
        SortOrder sortOrder,
        ProductType productType,
        String symbol,
        String orderBranchNo,
        String orderNo,
        String itemType,
        String exchangeCode,
        String continuationSearchKey,
        String continuationKey,
        String continuationHeader) {

    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public DailyOrderQuery {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(periodRange, "periodRange");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(fillStatus, "fillStatus");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(productType, "productType");
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("startDate must not be after endDate");
        symbol = valueOrEmpty(symbol);
        orderBranchNo = valueOrEmpty(orderBranchNo);
        orderNo = valueOrEmpty(orderNo);
        itemType = valueOrEmpty(itemType);
        exchangeCode = exchangeCode == null || exchangeCode.isBlank() ? "KRX" : exchangeCode;
        continuationSearchKey = valueOrEmpty(continuationSearchKey);
        continuationKey = valueOrEmpty(continuationKey);
        continuationHeader = valueOrEmpty(continuationHeader);
        if (!symbol.isEmpty() && !symbol.matches("\\d{6}")) {
            throw new IllegalArgumentException("symbol must be a six-digit KRX code");
        }
        if (!exchangeCode.matches("KRX|NXT|SOR|ALL")) {
            throw new IllegalArgumentException("exchangeCode must be KRX, NXT, SOR, or ALL");
        }
    }

    public static DailyOrderQuery all(LocalDate startDate, LocalDate endDate) {
        return new DailyOrderQuery(startDate, endDate, PeriodRange.WITHIN_THREE_MONTHS,
                Side.ALL, FillStatus.ALL, SortOrder.NEWEST_FIRST, ProductType.ALL,
                "", "", "", "", "KRX", "", "", "");
    }

    public String kisStartDate() { return startDate.format(KIS_DATE); }
    public String kisEndDate() { return endDate.format(KIS_DATE); }

    /** Returns a query for the next page when KIS reports more results. */
    public DailyOrderQuery nextPage(KisApiPage response) {
        String trCont = response.trCont();
        if (!"F".equalsIgnoreCase(trCont) && !"M".equalsIgnoreCase(trCont)) {
            throw new IllegalArgumentException("KIS response has no next page (tr_cont=" + trCont + ")");
        }
        JsonNode output = response.data().output2();
        String fk100 = output == null ? "" : output.path("ctx_area_fk100").asString("");
        String nk100 = output == null ? "" : output.path("ctx_area_nk100").asString("");
        if (fk100.isBlank() && nk100.isBlank()) {
            throw new IllegalArgumentException("KIS response did not contain continuation keys in output2");
        }
        return new DailyOrderQuery(startDate, endDate, periodRange, side, fillStatus, sortOrder, productType,
                symbol, orderBranchNo, orderNo, itemType, exchangeCode, fk100, nk100, "N");
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public enum PeriodRange {
        WITHIN_THREE_MONTHS("inner"), BEFORE_THREE_MONTHS("before");
        private final String value;
        PeriodRange(String value) { this.value = value; }
        public String value() { return value; }
    }

    public enum Side {
        ALL("00"), SELL("01"), BUY("02");
        private final String code;
        Side(String code) { this.code = code; }
        public String code() { return code; }
    }

    public enum FillStatus {
        ALL("00"), FILLED("01"), UNFILLED("02");
        private final String code;
        FillStatus(String code) { this.code = code; }
        public String code() { return code; }
    }

    public enum SortOrder {
        NEWEST_FIRST("00"), OLDEST_FIRST("01");
        private final String code;
        SortOrder(String code) { this.code = code; }
        public String code() { return code; }
    }

    public enum ProductType {
        ALL("00"), CASH("01"), CREDIT("02"), COLLATERAL("03"), LOAN("04"), BORROW("05"),
        SELF_FINANCING("06"), DISTRIBUTION_FINANCING("07");
        private final String code;
        ProductType(String code) { this.code = code; }
        public String code() { return code; }
    }
}
