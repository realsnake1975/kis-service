package dev.kis.openapi.domestic;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit, read-only calls against KIS. Run only with -Dkis.api.tests=true and PAPER credentials (KIS_PAPER_APP_KEY / KIS_PAPER_APP_SECRET).
 * Order submission/amendment/cancellation calls are intentionally excluded.
 */
@Tag("api")
@EnabledIfSystemProperty(named = "kis.api.tests", matches = "true")
@SpringBootTest(classes = KisApiCallIntegrationTest.TestApplication.class,
        properties = "spring.main.web-application-type=none")
class KisApiCallIntegrationTest {
    @Autowired
    private DomesticStockClient stocks;

    @Autowired
    private KisProperties properties;

    @BeforeEach
    void requirePaperEnvironment() {
        assertTrue(properties.getEnvironment() == KisEnvironment.PAPER,
                "KIS API integration tests are restricted to PAPER environment");
    }

    @Test
    void readsDomesticStockPrice() {
        var response = stocks.getPrice("005930").block(Duration.ofSeconds(30));

        assertNotNull(response);
        assertTrue(response.isSuccess(), () -> "KIS error: " + response.msgCd() + " " + response.msg1());
        assertNotNull(response.output());
    }

    @Test
    void readsDomesticAccountBalance() {
        var response = stocks.getBalance().block(Duration.ofSeconds(30));

        assertNotNull(response);
        assertTrue(response.isSuccess(), () -> "KIS error: " + response.msgCd() + " " + response.msg1());
    }

    @Test
    void readsRecentDailyOrders() {
        LocalDate today = LocalDate.now();
        DailyOrderQuery query = DailyOrderQuery.all(today.minusDays(7), today);
        var page = stocks.getDailyOrders(query).block(Duration.ofSeconds(30));

        assertNotNull(page);
        assertNotNull(page.data());
        assertTrue(page.data().isSuccess(),
                () -> "KIS error: " + page.data().msgCd() + " " + page.data().msg1());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
