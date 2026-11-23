package dev.kis.openapi.domestic;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads and searches KIS's daily KOSPI/KOSDAQ security master files. */
@Service
public class DomesticStockMasterClient {
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Charset KOREAN = Charset.forName("MS949");
    private static final String KOSPI_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSDAQ_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";
    private static final WebClient WEB_CLIENT = WebClient.builder().build();

    private final AtomicReference<Mono<List<DomesticStock>>> inFlight = new AtomicReference<>();
    private volatile List<DomesticStock> cachedStocks;
    private volatile Instant cachedAt;

    /** Returns all entries; the first request downloads both exchange masters. */
    public Mono<List<DomesticStock>> getAll() {
        List<DomesticStock> current = cachedStocks;
        Instant loadedAt = cachedAt;
        if (current != null && loadedAt != null && loadedAt.plus(CACHE_TTL).isAfter(Instant.now())) {
            return Mono.just(current);
        }
        synchronized (this) {
            current = cachedStocks;
            loadedAt = cachedAt;
            if (current != null && loadedAt != null && loadedAt.plus(CACHE_TTL).isAfter(Instant.now())) {
                return Mono.just(current);
            }
            Mono<List<DomesticStock>> pending = inFlight.get();
            if (pending == null) {
                pending = downloadAll()
                        .doOnNext(stocks -> { cachedStocks = stocks; cachedAt = Instant.now(); })
                        .doFinally(signal -> inFlight.set(null))
                        .cache();
                inFlight.set(pending);
            }
            return pending;
        }
    }

    /** Searches by exact six-character code and/or a case-insensitive partial name. */
    public Mono<List<DomesticStock>> search(String name, String code) {
        String normalizedName = normalize(name);
        String normalizedCode = normalize(code);
        return getAll().map(stocks -> filter(stocks, normalizedName, normalizedCode));
    }

    static List<DomesticStock> filter(List<DomesticStock> stocks, String name, String code) {
        String normalizedName = normalize(name);
        String normalizedCode = normalize(code);
        return stocks.stream()
                .filter(stock -> normalizedCode == null || stock.code().equalsIgnoreCase(normalizedCode))
                .filter(stock -> normalizedName == null || stock.name().toLowerCase(Locale.ROOT).contains(normalizedName.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** Forces an immediate refresh from KIS, bypassing the 24-hour in-memory cache. */
    public Mono<List<DomesticStock>> refresh() {
        synchronized (this) {
            cachedStocks = null;
            cachedAt = null;
            inFlight.set(null);
        }
        return getAll();
    }

    private Mono<List<DomesticStock>> downloadAll() {
        Mono<byte[]> kospi = WEB_CLIENT.get().uri(KOSPI_URL).retrieve().bodyToMono(byte[].class);
        Mono<byte[]> kosdaq = WEB_CLIENT.get().uri(KOSDAQ_URL).retrieve().bodyToMono(byte[].class);
        return Mono.zip(kospi, kosdaq)
                .map(files -> {
                    List<DomesticStock> stocks = new ArrayList<>();
                    stocks.addAll(parseMaster(unzip(files.getT1()), DomesticStock.Market.KOSPI));
                    stocks.addAll(parseMaster(unzip(files.getT2()), DomesticStock.Market.KOSDAQ));
                    stocks.sort(Comparator.comparing(DomesticStock::code));
                    return List.copyOf(stocks);
                });
    }

    static byte[] unzip(byte[] archive) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null) throw new IllegalArgumentException("KIS master archive is empty");
            return zip.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read KIS master archive", e);
        }
    }

    static List<DomesticStock> parseMaster(byte[] content, DomesticStock.Market market) {
        int trailingCharacters = market == DomesticStock.Market.KOSPI ? 228 : 222;
        List<DomesticStock> stocks = new ArrayList<>();
        for (String line : new String(content, KOREAN).split("\\R")) {
            if (line.length() <= 21 + trailingCharacters) continue;
            String code = line.substring(0, 9).trim();
            String standardCode = line.substring(9, 21).trim();
            String name = line.substring(21, line.length() - trailingCharacters).trim();
            if (code.length() > 6) code = code.substring(code.length() - 6);
            if (code.matches("\\d{6}") && !name.isBlank()) {
                stocks.add(new DomesticStock(code, standardCode, name, market));
            }
        }
        return List.copyOf(stocks);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
