# kis-service

Spring Boot starter for the Korea Investment & Securities domestic-stock REST API. The domestic-stock slice includes cached OAuth access tokens, current price, cash buy/sell orders, account balance lookup, order amendment/cancellation, and daily order/fill history. WebSocket and overseas stocks are intentionally left for later.

## Requirements and build

- Java 17+
- Spring Boot 4.1.2
- Gradle Wrapper 9.9.1

Build the library with `gradlew.bat clean build` on Windows or `./gradlew clean build` on macOS/Linux. Publish it to the local Maven repository with `gradlew.bat publishToMavenLocal` or `./gradlew publishToMavenLocal`.

Run JUnit 5 unit tests with `gradlew.bat test`. KIS API-call tests are separate and read-only; run `gradlew.bat apiTest` after setting the PAPER app key/secret and account environment variables below. The API-call test suite refuses to run against `REAL`.

## Configuration

Copy `src/main/resources/application-kis-example.yml` into the consuming app as `application.yml`, or configure the same properties in an external config file. KIS app keys, app secrets and account numbers are read from `kis.openapi.paper.*` / `kis.openapi.real.*`. PAPER and REAL are configured separately; `PAPER` is the default, and the library does not silently fall back between environments. MySQL is only needed for the optional quote history.

Example environment variables:

```powershell
$env:KIS_ENVIRONMENT = 'PAPER'
$env:KIS_PAPER_APP_KEY = '...'
$env:KIS_PAPER_APP_SECRET = '...'
$env:KIS_PAPER_ACCOUNT_NUMBER = '12345678'
$env:KIS_PAPER_ACCOUNT_PRODUCT_CODE = '01'
```

Never commit a plain app key or secret. Either supply them through the environment as above, or store them in the config file as Jasypt `ENC(...)` values (see the dashboard section). For real trading, set the `kis.openapi.real.*` values and `KIS_ENVIRONMENT=REAL`.

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

## Domestic stock master

`DomesticStockMasterClient` downloads the official KOSPI and KOSDAQ master files from KIS (no API credentials required), parses the daily CP949 fixed-width files, and keeps the result in memory for 24 hours. The first lookup needs an internet connection. Call `refresh()` to force a download sooner.

```java
Mono<List<DomesticStock>> all = stockMaster.getAll();
Mono<List<DomesticStock>> samsung = stockMaster.search("삼성전자", null); // partial, case-insensitive name
Mono<List<DomesticStock>> byCode = stockMaster.search(null, "005930"); // exact code
Mono<List<DomesticStock>> allMatches = stockMaster.search(null, null); // no filters: all entries
```

When both name and code are supplied, both filters apply. Each result includes the short code, standard code, Korean name, and market (`KOSPI` or `KOSDAQ`). The data includes the securities present in KIS's exchange master files and is refreshed at most once per 24 hours unless `refresh()` is called.

## Scheduled domestic stock prices

To poll selected stocks once per minute, enable the optional monitor in the consuming application's configuration. It is disabled by default. Each code is queried sequentially with a small spacing between requests, and each result or request error is published as a `DomesticStockPriceEvent` through Spring's application event system.

```yaml
kis:
  openapi:
    domestic-stock-monitor:
      enabled: true
      interval: 1m
      symbols: ["005930", "000660"]
```

Consume results with an event listener:

```java
@Component
class StockPriceListener {
    @EventListener
    void onPrice(DomesticStockPriceEvent event) {
        if (event.isSuccess()) {
            System.out.println(event.symbol() + ": " + event.response().output());
        } else {
            System.err.println(event.symbol() + " lookup failed: " + event.errorMessage());
        }
    }
}
```

The first polling cycle starts when the application starts; subsequent cycles start on the configured interval. Codes are queried sequentially with a short spacing. If a cycle is still running at the next tick, that tick is skipped so requests never overlap or build up in a queue. The interval must be positive and codes must be six digits.

### Optional MySQL history

Scheduled successful responses can also be appended to `kis_stock_price_history` with a JPA entity and Spring Data JPA repository. Enable persistence and configure a MySQL datasource. Spring Boot's JDBC datasource uses HikariCP; credentials should come from environment variables.

```yaml
kis:
  openapi:
    domestic-stock-monitor:
      persistence:
        enabled: true
spring:
  datasource:
    url: ${KIS_DB_URL:jdbc:mysql://localhost:3306/kis_service?serverTimezone=Asia/Seoul&characterEncoding=UTF-8}
    username: ${KIS_DB_USERNAME:kis_service}
    password: ${KIS_DB_PASSWORD:}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: update
```

The entity maps to `kis_stock_price_history`; Hibernate creates or updates the table with `ddl-auto: update`. Each successful scheduled quote stores its code, master-file name, current price, signed change, change rate, observation time, and original KIS `output` JSON. Database writes run on Reactor's bounded-elastic scheduler so they do not block the price polling thread. Failed KIS calls are published as events and are not saved as quotes.

The Gradle runtime includes MySQL Connector/J 26.10.0, the current GA driver at the time of this change; Oracle states that Connector/J 26.10 works with MySQL Server 8.0 and later. If you manage the driver separately, use [the official Connector/J compatibility guidance](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html).

## Initial API surface

- `getPrice(symbol)`: domestic stock quote (`FHKST01010100`)
- `placeCashOrder(request)`: domestic cash buy/sell, market or limit
- `getBalance()`: domestic stock balance
- `amendOrCancelOrder(request)`: amend (`01`) or cancel (`02`) an existing domestic cash order
- `getDailyOrders(query)`: daily order/fill history with explicit continuation page support
- `DomesticStockMasterClient.getAll()`: all KOSPI/KOSDAQ master entries
- `DomesticStockMasterClient.search(name, code)`: filter by optional partial name and exact code
- `DomesticStockPriceMonitor`: optional periodic quote polling, disabled unless configured
- Optional HikariCP/MySQL persistence for scheduled successful quote history

## Dashboard (React + Spring WebFlux)

The optional `dashboard-api` application provides a responsive React/Vite dashboard. It includes Google/GitHub OAuth login, a read-only masked status of the configured KIS credentials, domestic stock master search, a JPA-backed watchlist that updates the one-minute quote monitor, and daily/weekly/monthly/quarterly/yearly price history charts. The dashboard does not place orders.

The KIS `REAL` app key, app secret and account number, the MySQL URL/username/password, and the Google/GitHub OAuth client IDs and secrets are stored encrypted with [Jasypt](https://github.com/ulisesbocchio/jasypt-spring-boot) as `ENC(...)` values in `dashboard-api/src/main/resources/application.yml`. Only the Jasypt master password is needed at runtime:

```powershell
$env:JASYPT_ENCRYPTOR_PASSWORD = '...'
```

Keep this password out of source control; without it the application cannot start. The values use `PBEWITHHMACSHA512ANDAES_256` with 100,000 key-obtention iterations, random salt/IV, and base64 output. To change a value, encrypt it with a `StandardPBEStringEncryptor` configured the same way (see `EncryptedApplicationPropertiesTest`) and replace the matching `ENC(...)` entry. To verify that the committed values decrypt, run `./gradlew.bat :dashboard-api:test` with `JASYPT_ENCRYPTOR_PASSWORD` set; `EncryptedApplicationPropertiesTest` and `OAuthConfigurationIntegrationTest` are skipped when it is absent.

The dashboard runs against `REAL` by default (`kis.openapi.environment: ${KIS_ENVIRONMENT:REAL}`), because only REAL credentials are configured there. To use a PAPER account instead, add encrypted `kis.openapi.paper.app-key` / `app-secret` / `account-number` entries and set `KIS_ENVIRONMENT=PAPER`. Changing a credential means re-encrypting it, replacing the `ENC(...)` value, and restarting the API; the dashboard cannot edit credentials.

Start the API and frontend in separate terminals:

```powershell
./gradlew.bat :dashboard-api:bootRun
cd dashboard-ui
pnpm install
pnpm dev
```

Open `http://localhost:5173`. Register OAuth callback URLs as `http://localhost:5173/login/oauth2/code/google` and `http://localhost:5173/login/oauth2/code/github` for local Vite use. Set `KIS_GOOGLE_REDIRECT_URI` and `KIS_GITHUB_REDIRECT_URI` when deploying behind a different public URL. Do not commit plain provider or KIS secrets. The API stores the watchlist in `kis_dashboard_watch`; scheduled quotes use the `kis_stock_price_history` table.

The UI E2E suite mocks OAuth sessions, the stock master, credentials and trend responses; it does not call KIS or a provider account:

```powershell
cd dashboard-ui
pnpm install
pnpm exec playwright install chromium
pnpm test:e2e
```

Backend dashboard controller tests run with `./gradlew.bat :dashboard-api:test`; all library unit tests run with `./gradlew.bat test`.

Responses preserve KIS's `output`, `output1`, and `output2` JSON as `JsonNode` because fields vary by API. Non-zero KIS `rt_cd` responses are raised as `KisApiException`.

## Notes

- REST calls use Spring `WebClient` and return Reactor `Mono`.
- OAuth token acquisition uses `WebClient` and blocks only on a bounded-elastic scheduler during a cache miss.
- WebSocket, overseas-stock APIs, amendable/cancellable quantity lookup, buying-power checks, hashkey support, and transaction-rate governance are not implemented yet.
- Transaction IDs and request fields are based on the current KIS `open-trading-api` examples. Confirm them against KIS Developers before live use because KIS may change API requirements.
