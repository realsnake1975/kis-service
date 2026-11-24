package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiPage;
import dev.kis.openapi.KisApiResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyOrderQueryTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    DailyOrderQueryTest() throws Exception {
    }

    @Test
    void defaultsToAllOrdersAndFormatsDatesForKis() {
        DailyOrderQuery query = DailyOrderQuery.all(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5));

        assertEquals("20260102", query.kisStartDate());
        assertEquals("20260105", query.kisEndDate());
        assertEquals(DailyOrderQuery.Side.ALL, query.side());
        assertEquals(DailyOrderQuery.FillStatus.ALL, query.fillStatus());
        assertEquals("KRX", query.exchangeCode());
    }

    @Test
    void rejectsInvertedDateRange() {
        assertThrows(IllegalArgumentException.class, () -> DailyOrderQuery.all(
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 2)));
    }

    @Test
    void createsNextPageQueryFromKisContinuationData() throws Exception {
        var body = jsonMapper.readTree("""
                {"rt_cd":"0","output1":[],"output2":{"ctx_area_fk100":"FKEY","ctx_area_nk100":"NKEY"}}
                """);
        KisApiResponse apiResponse = jsonMapper.treeToValue(body, KisApiResponse.class);
        DailyOrderQuery query = DailyOrderQuery.all(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5));

        DailyOrderQuery next = query.nextPage(new KisApiPage(apiResponse, "F"));

        assertEquals("FKEY", next.continuationSearchKey());
        assertEquals("NKEY", next.continuationKey());
        assertEquals("N", next.continuationHeader());
    }

    @Test
    void readsContinuationKeysFromTheTopLevelOfTheBody() {
        KisApiResponse apiResponse = jsonMapper.readValue("""
                {"rt_cd":"0","output1":[],"output2":{},"ctx_area_fk100":"FKEY  ","ctx_area_nk100":"NKEY  "}
                """, KisApiResponse.class);
        DailyOrderQuery query = DailyOrderQuery.all(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5));

        DailyOrderQuery next = query.nextPage(new KisApiPage(apiResponse, "M"));

        assertEquals("FKEY", next.continuationSearchKey());
        assertEquals("NKEY", next.continuationKey());
    }

    @Test
    void rejectsNextPageWhenKisReportsNoMoreRows() {
        KisApiResponse apiResponse = new KisApiResponse("0", "0", "ok", null, null, null);
        DailyOrderQuery query = DailyOrderQuery.all(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5));

        assertThrows(IllegalArgumentException.class, () -> query.nextPage(new KisApiPage(apiResponse, "D")));
    }
}
