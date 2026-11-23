package dev.kis.openapi.domestic;

import dev.kis.openapi.KisApiResponse;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomesticStockPriceJpaPersistenceTest {
    @Test
    void persistsAndReloadsQuoteUsingJpaEntityMapping() throws Exception {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:kis_stock_price_test;DB_CLOSE_DELAY=-1")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.show_sql", "false")
                .build();

        try (SessionFactory sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(DomesticStockPriceEntity.class)
                .buildMetadata()
                .buildSessionFactory()) {
            var output = new JsonMapper().readTree("""
                    {"stck_prpr":"70100","prdy_vrss":"1000","prdy_vrss_sign":"2","prdy_ctrt":"1.45"}
                    """);
            DomesticStockPriceEntity entity = DomesticStockPriceEntity.from(
                    new DomesticStockPriceEvent("005930", Instant.parse("2026-11-15T01:00:00Z"),
                            new KisApiResponse("0", "0", "ok", output, null, null), null),
                    "삼성전자");
            Long id;
            try (var session = sessionFactory.openSession()) {
                var transaction = session.beginTransaction();
                session.persist(entity);
                transaction.commit();
                id = entity.getId();
            }

            assertNotNull(id);
            try (var session = sessionFactory.openSession()) {
                DomesticStockPriceEntity loaded = session.find(DomesticStockPriceEntity.class, id);
                assertEquals("005930", loaded.getStockCode());
                assertEquals("삼성전자", loaded.getStockName());
                assertEquals("70100.0000", loaded.getCurrentPrice().toPlainString());
                assertEquals("1000.0000", loaded.getPriceChange().toPlainString());
                assertEquals("1.450000", loaded.getChangeRate().toPlainString());
                assertEquals(output.toString(), loaded.getRawOutput());
                assertTrue(loaded.getObservedAt().toString().startsWith("2026-11-15T01:00:00"));
            }

        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
