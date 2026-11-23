package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "kis_stock_price_history", indexes = {
        @Index(name = "idx_kis_stock_price_code_time", columnList = "stock_code, observed_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomesticStockPriceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Column(name = "current_price", precision = 18, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "price_change", precision = 18, scale = 4)
    private BigDecimal priceChange;

    @Column(name = "change_rate", precision = 12, scale = 6)
    private BigDecimal changeRate;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "raw_output", nullable = false, columnDefinition = "TEXT")
    private String rawOutput;

    public static DomesticStockPriceEntity from(DomesticStockPriceEvent event, String stockName) {
        KisApiResponse response = event.response();
        JsonNode output = response == null ? null : response.output();
        if (output == null) throw new IllegalArgumentException("Successful quote event must contain output data");

        DomesticStockPriceEntity entity = new DomesticStockPriceEntity();
        entity.stockCode = event.symbol();
        entity.stockName = stockName;
        entity.currentPrice = decimal(output, "stck_prpr");
        // KIS sends prdy_vrss already signed (e.g. "-2500" with sign "5"), but derive the sign from
        // prdy_vrss_sign so the result is correct whether or not the value carries its own sign.
        BigDecimal change = decimal(output, "prdy_vrss");
        String sign = text(output, "prdy_vrss_sign");
        if (change != null) {
            change = change.abs();
            if ("4".equals(sign) || "5".equals(sign)) change = change.negate();
        }
        entity.priceChange = change;
        entity.changeRate = decimal(output, "prdy_ctrt");
        entity.observedAt = event.observedAt();
        entity.rawOutput = output.toString();
        return entity;
    }

    private static BigDecimal decimal(JsonNode output, String field) {
        String value = text(output, field);
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String text(JsonNode output, String field) {
        JsonNode value = output.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
