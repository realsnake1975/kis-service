package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomesticStockPriceEntityTest {
    @Test
    void mapsKisQuoteFieldsIntoEntityAndKeepsRawOutput() throws Exception {
        var output = new JsonMapper().readTree("""
                {"stck_prpr":"70,100","prdy_vrss":"1,000","prdy_vrss_sign":"4","prdy_ctrt":"-1.39"}
                """);
        Instant observedAt = Instant.parse("2026-11-15T01:00:00Z");
        DomesticStockPriceEvent event = new DomesticStockPriceEvent("005930", observedAt,
                new KisApiResponse("0", "0", "ok", output, null, null), null);

        DomesticStockPriceEntity entity = DomesticStockPriceEntity.from(event, "삼성전자");

        assertEquals("005930", entity.getStockCode());
        assertEquals("삼성전자", entity.getStockName());
        assertEquals(new BigDecimal("70100"), entity.getCurrentPrice());
        assertEquals(new BigDecimal("-1000"), entity.getPriceChange());
        assertEquals(new BigDecimal("-1.39"), entity.getChangeRate());
        assertEquals(observedAt, entity.getObservedAt());
        assertEquals(output.toString(), entity.getRawOutput());
    }

    @Test
    void leavesMissingOrNonNumericQuoteFieldsNull() throws Exception {
        var output = new JsonMapper().readTree("""
                {"stck_prpr":"unknown","prdy_vrss":"","prdy_ctrt":null}
                """);
        DomesticStockPriceEvent event = new DomesticStockPriceEvent("005930", Instant.now(),
                new KisApiResponse("0", "0", "ok", output, null, null), null);

        DomesticStockPriceEntity entity = DomesticStockPriceEntity.from(event, "삼성전자");

        org.junit.jupiter.api.Assertions.assertNull(entity.getCurrentPrice());
        org.junit.jupiter.api.Assertions.assertNull(entity.getPriceChange());
        org.junit.jupiter.api.Assertions.assertNull(entity.getChangeRate());
    }

    @Test
    void rejectsResponseWithoutOutput() {
        DomesticStockPriceEvent event = new DomesticStockPriceEvent("005930", Instant.now(),
                new KisApiResponse("0", "0", "ok", null, null, null), null);

        assertThrows(IllegalArgumentException.class, () -> DomesticStockPriceEntity.from(event, "삼성전자"));
    }
}
