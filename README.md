# kis-service

Spring Boot starter for the Korea Investment & Securities domestic-stock REST API. The domestic-stock slice includes cached OAuth access tokens, current price, cash buy/sell orders, account balance lookup, order amendment/cancellation, and daily order/fill history. WebSocket and overseas stocks are intentionally left for later.

## Requirements and build

- Java 17+
- Spring Boot 4.1.2
- Gradle Wrapper 9.9.1

Build the library with `gradlew.bat clean build` on Windows or `./gradlew clean build` on macOS/Linux. Publish it to the local Maven repository with `gradlew.bat publishToMavenLocal` or `./gradlew publishToMavenLocal`.

Run JUnit 5 unit tests with `gradlew.bat test`. KIS API-call tests are separate and read-only; run `gradlew.bat apiTest` only after setting the `KIS_PAPER_*` environment variables shown below. The API-call test suite refuses to run against `REAL`.

## Configuration

Copy `src/main/resources/application-kis-example.yml` into the consuming app as `application.yml`, or configure the same properties in an external config file. Keep credentials outside source control. The environment and credentials/accounts are separate for paper and live trading; `PAPER` is the default.

Example environment variables:

```powershell
$env:KIS_ENVIRONMENT = 'PAPER'
$env:KIS_PAPER_APP_KEY = '...'
$env:KIS_PAPER_APP_SECRET = '...'
$env:KIS_PAPER_ACCOUNT_NUMBER = '12345678'
$env:KIS_PAPER_ACCOUNT_PRODUCT_CODE = '01'
```

For real trading, set `KIS_ENVIRONMENT=REAL` and provide the separate `KIS_REAL_*` values. The library does not silently fall back between environments.

## Use from a Spring application

Add this project as a Gradle dependency after publishing it locally, then inject `DomesticStockClient`:

```groovy
repositories { mavenLocal() }
dependencies { implementation 'dev.kis:kis-service:0.1.0-SNAPSHOT' }
```

```java
@Service
class TradingService {
    private final DomesticStockClient stocks;

    TradingService(DomesticStockClient stocks) {
        this.stocks = stocks;
    }

    Mono<KisApiResponse> samsungPrice() {
        return stocks.getPrice("005930");
    }

    Mono<KisApiResponse> buyOneShareAtLimit() {
        return stocks.placeCashOrder(new CashOrderRequest(
                "005930", CashOrderRequest.Side.BUY,
                CashOrderRequest.OrderType.LIMIT, 1, new BigDecimal("70000")));
    }

    Mono<KisApiResponse> cancelRemainingOrder(String branchNo, String orderNo) {
        return stocks.amendOrCancelOrder(new OrderAmendCancelRequest(
                branchNo, orderNo, CashOrderRequest.OrderType.LIMIT,
                OrderAmendCancelRequest.Action.CANCEL, 0, BigDecimal.ZERO, true,
                OrderAmendCancelRequest.Exchange.KRX));
    }

    Mono<KisApiPage> recentOrders(LocalDate from, LocalDate to) {
        return stocks.getDailyOrders(DailyOrderQuery.all(from, to));
    }
}
```

`DailyOrderQuery.all(from, to)` starts with all sides, all fill states, newest-first, and all product types. If `KisApiPage.trCont()` is `F` or `M`, pass `query.nextPage(page)` to `getDailyOrders` for the next page. Select `PeriodRange.BEFORE_THREE_MONTHS` for older history; KIS uses a separate transaction ID for that range.

Do not call a real order until the selected `kis.openapi.environment`, app credentials, account number, order type, quantity, and price have been reviewed. Market orders use price `0`; limit orders require a positive price. Amend/cancel calls require the original KIS order number and its branch number from the order response/history. Check the remaining amendable/cancellable quantity with KIS before submitting.

## Token cache

OAuth tokens are cached through Spring's cache abstraction and Caffeine under a key for each environment. The cache expires after 23 hours, providing a one-hour margin against the one-day token lifetime described in KIS's Python sample. Failed token requests are not cached. If the consuming app supplies another `CacheManager`, this auto-configuration uses it; ensure its `kisAccessTokens` cache has a suitably short TTL.

## Initial API surface

- `getPrice(symbol)`: domestic stock quote (`FHKST01010100`)
- `placeCashOrder(request)`: domestic cash buy/sell, market or limit
- `getBalance()`: domestic stock balance
- `amendOrCancelOrder(request)`: amend (`01`) or cancel (`02`) an existing domestic cash order
- `getDailyOrders(query)`: daily order/fill history with explicit continuation page support

Responses preserve KIS's `output`, `output1`, and `output2` JSON as `JsonNode` because fields vary by API. Non-zero KIS `rt_cd` responses are raised as `KisApiException`.

## Notes

- REST calls use Spring `WebClient` and return Reactor `Mono`.
- OAuth token acquisition uses `WebClient` and blocks only on a bounded-elastic scheduler during a cache miss.
- WebSocket, overseas-stock APIs, amendable/cancellable quantity lookup, buying-power checks, hashkey support, and transaction-rate governance are not implemented yet.
- Transaction IDs and request fields are based on the current KIS `open-trading-api` examples. Confirm them against KIS Developers before live use because KIS may change API requirements.
