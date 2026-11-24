package dev.kis.dashboard;

import dev.kis.openapi.domestic.DomesticStock;
import dev.kis.openapi.domestic.DomesticStockPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardControllerTest {
    private final AtomicReference<String> searchedQuery = new AtomicReference<>();
    private final AtomicReference<List<Object>> trendArguments = new AtomicReference<>();
    private final AtomicReference<List<String>> appliedSchedule = new AtomicReference<>();
    private final java.util.ArrayList<DashboardWatch> watches = new java.util.ArrayList<>();
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        DomesticStockSearchService stocks = query -> {
            searchedQuery.set(query);
            return Mono.just(List.of(new DomesticStock("005930", "KR7005930003", "삼성전자", DomesticStock.Market.KOSPI)));
        };
        DomesticStockPriceRepository history = proxy(DomesticStockPriceRepository.class, (method, args) -> {
            if (method.equals("findByStockCodeAndObservedAtBetweenOrderByObservedAtAsc")) {
                trendArguments.set(List.of(args));
                return List.of();
            }
            if (method.equals("findFirstByStockCodeOrderByObservedAtDesc")) return java.util.Optional.empty();
            throw new UnsupportedOperationException(method);
        });
        DashboardWatchRepository watchRepository = proxy(DashboardWatchRepository.class, (method, args) -> switch (method) {
            case "findAll" -> List.copyOf(watches);
            case "deleteAll" -> { watches.clear(); yield null; }
            case "saveAll" -> {
                for (Object entry : (Iterable<?>) args[0]) watches.add((DashboardWatch) entry);
                yield List.copyOf(watches);
            }
            default -> throw new UnsupportedOperationException(method);
        });
        PriceSchedule schedule = appliedSchedule::set;
        controller = new DashboardController(stocks, history, watchRepository, schedule);
    }

    @Test
    void searchesByQueryAndReturnsMasterResults() {
        var result = controller.searchStocks("삼성").block();
        assertEquals("삼성", searchedQuery.get());
        assertEquals("삼성전자", result.get(0).name());
    }

    @Test
    void trendQueriesRequestedCodeForSelectedPeriod() {
        assertTrue(controller.trend("005930", "WEEK").block().isEmpty());
        List<Object> args = trendArguments.get();
        assertEquals("005930", args.get(0));
        Instant from = (Instant) args.get(1);
        Instant to = (Instant) args.get(2);
        assertTrue(from.isBefore(to));
        assertTrue(from.isAfter(to.minusSeconds(7 * 24 * 60 * 60 + 5)));
    }

    @Test
    void savingSchedulePersistsSelectedCodesAndUpdatesMonitor() {
        var result = controller.updateSchedule(new DashboardController.ScheduleRequest(List.of("005930"))).block();
        assertEquals(List.of("005930"), appliedSchedule.get());
        assertEquals("삼성전자", result.get(0).name());
        assertEquals("005930", watches.get(0).getCode());
    }

    @Test
    void getScheduleReturnsPersistedWatchlist() {
        watches.add(new DashboardWatch("005930", "삼성전자", "KOSPI"));
        var result = controller.getSchedule().block();
        assertEquals("005930", result.get(0).code());
        assertEquals("삼성전자", result.get(0).name());
    }

    @Test
    void rejectsInvalidStockCodesBeforeChangingSchedule() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateSchedule(new DashboardController.ScheduleRequest(List.of("ABC123"))));
        assertTrue(appliedSchedule.get() == null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "toString" -> type.getSimpleName() + " test double";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
            return invocation.invoke(method.getName(), args == null ? new Object[0] : args);
        });
    }

    @FunctionalInterface
    private interface ProxyInvocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
