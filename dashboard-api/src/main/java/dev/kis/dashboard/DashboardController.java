package dev.kis.dashboard;

import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.auth.KisApiCredential;
import dev.kis.openapi.auth.KisCredentialRepository;
import dev.kis.openapi.domestic.DomesticStockPriceEntity;
import dev.kis.openapi.domestic.DomesticStockPriceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
public class DashboardController {
    private final KisCredentialRepository credentials;
    private final DomesticStockSearchService stockMaster;
    private final DomesticStockPriceRepository history;
    private final DashboardWatchRepository watchRepository;
    private final PriceSchedule priceSchedule;

    public DashboardController(KisCredentialRepository credentials, DomesticStockSearchService stockMaster,
                               DomesticStockPriceRepository history, DashboardWatchRepository watchRepository,
                               PriceSchedule priceSchedule) {
        this.credentials = credentials;
        this.stockMaster = stockMaster;
        this.history = history;
        this.watchRepository = watchRepository;
        this.priceSchedule = priceSchedule;
    }

    @GetMapping("/api/admin/credentials/{environment}")
    Mono<Map<String, Object>> getCredentials(@PathVariable KisEnvironment environment) {
        return Mono.fromCallable(() -> credentials.findById(environment)
                .<Map<String, Object>>map(c -> Map.of("environment", environment.name(), "appKey", mask(c.getAppKey()), "configured", true))
                .orElse(Map.of("environment", environment.name(), "appKey", "", "configured", false)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/api/admin/credentials/{environment}")
    Mono<Map<String, Boolean>> saveCredentials(@PathVariable KisEnvironment environment, @RequestBody CredentialRequest request) {
        if (request.appKey() == null || request.appKey().isBlank() || request.appSecret() == null || request.appSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "App key and secret are required");
        }
        return Mono.fromCallable(() -> {
            credentials.save(KisApiCredential.create(environment, request.appKey().trim(), request.appSecret().trim()));
            return Map.of("saved", true);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/stocks/search")
    Mono<List<dev.kis.openapi.domestic.DomesticStock>> searchStocks(@RequestParam(required = false) String q) {
        return stockMaster.search(q);
    }

    @GetMapping("/api/schedule")
    Mono<List<WatchedStock>> getSchedule() {
        return Mono.fromCallable(() -> watchRepository.findAll().stream()
                .map(stock -> new WatchedStock(stock.getCode(), stock.getName(), stock.getMarket(),
                        history.findFirstByStockCodeOrderByObservedAtDesc(stock.getCode())
                                .map(dev.kis.openapi.domestic.DomesticStockPriceEntity::getCurrentPrice).orElse(null))).toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/api/schedule")
    Mono<List<WatchedStock>> updateSchedule(@RequestBody ScheduleRequest request) {
        List<String> codes = request.codes() == null ? List.of() : request.codes().stream().distinct().toList();
        if (codes.stream().anyMatch(code -> code == null || !code.matches("\\d{6}"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock codes must contain six digits");
        }
        return Flux.fromIterable(codes)
                .concatMap(code -> stockMaster.search(code).map(found -> {
                    if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stock code: " + code);
                    var stock = found.get(0);
                    return new WatchedStock(stock.code(), stock.name(), stock.market().name(), null);
                }))
                .collectList()
                .flatMap(stocks -> Mono.fromCallable(() -> {
                    watchRepository.deleteAll();
                    watchRepository.saveAll(stocks.stream().map(s -> new DashboardWatch(s.code(), s.name(), s.market())).toList());
                    priceSchedule.updateSymbols(stocks.stream().map(WatchedStock::code).toList());
                    return stocks;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/api/stocks/{code}/trend")
    Mono<List<TrendPoint>> trend(@PathVariable String code, @RequestParam(defaultValue = "DAY") String period) {
        ChronoUnit unit = switch (period.toUpperCase()) {
            case "WEEK", "MONTH", "QUARTER" -> ChronoUnit.DAYS;
            case "YEAR" -> ChronoUnit.MONTHS;
            default -> ChronoUnit.DAYS;
        };
        long amount = switch (period.toUpperCase()) { case "WEEK" -> 7; case "MONTH" -> 30; case "QUARTER" -> 90; case "YEAR" -> 12; default -> 1; };
        Instant to = Instant.now();
        return Mono.fromCallable(() -> history.findByStockCodeAndObservedAtBetweenOrderByObservedAtAsc(code, to.minus(amount, unit), to)
                .stream().collect(java.util.stream.Collectors.toMap(
                        quote -> bucket(quote.getObservedAt(), period),
                        dev.kis.openapi.domestic.DomesticStockPriceEntity::getCurrentPrice,
                        (first, last) -> last,
                        LinkedHashMap::new))
                .entrySet().stream().map(entry -> new TrendPoint(entry.getKey(), entry.getValue())).toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Instant bucket(Instant instant, String period) {
        ZoneId marketZone = ZoneId.of("Asia/Seoul");
        LocalDateTime local = LocalDateTime.ofInstant(instant, marketZone);
        return switch (period.toUpperCase()) {
            case "DAY" -> local.truncatedTo(ChronoUnit.HOURS).atZone(marketZone).toInstant();
            case "YEAR" -> YearMonth.from(local).atDay(1).atStartOfDay(marketZone).toInstant();
            default -> local.toLocalDate().atStartOfDay(marketZone).toInstant();
        };
    }

    private String mask(String value) { return value == null || value.length() < 8 ? "••••••••" : value.substring(0, 4) + "••••" + value.substring(value.length() - 4); }
    public record CredentialRequest(String appKey, String appSecret) { }
    public record TrendPoint(Instant time, java.math.BigDecimal price) { }
    public record ScheduleRequest(List<String> codes) { }
    public record WatchedStock(String code, String name, String market, java.math.BigDecimal price) { }
}
