package dev.kis.dashboard;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import dev.kis.openapi.auth.KisAccount;
import dev.kis.openapi.auth.KisAccountContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountControllerTest {
    private final JsonMapper json = JsonMapper.builder().build();

    private KisProperties properties() {
        KisProperties properties = new KisProperties();
        properties.setEnvironment(KisEnvironment.REAL);
        return properties;
    }

    private JsonNode node(String value) { return json.readTree(value); }

    private final Authentication me = new TestingAuthenticationToken("github:1", null, "ROLE_USER");
    private final KisAccount myAccount = new KisAccount("my-key", "my-secret", "12345678", "01");
    private final java.util.concurrent.atomic.AtomicReference<KisAccount> usedAccount = new java.util.concurrent.atomic.AtomicReference<>();

    /** Records the account passed through the Reactor context, like the real KIS client would use. */
    private AccountBalanceSource source(KisApiResponse response) {
        return () -> Mono.deferContextual(context -> {
            usedAccount.set(KisAccountContext.from(context).orElse(null));
            return Mono.just(response);
        });
    }

    private AccountController controller(AccountBalanceSource source) {
        return new AccountController(source, properties(),
                (userKey, environment) -> userKey.equals("github:1") ? Optional.of(myAccount) : Optional.empty());
    }

    @Test
    void mapsHoldingsAndSummaryAndMasksTheAccountNumber() {
        var response = new KisApiResponse("0", "KIOK0510", "조회가 완료되었습니다", null,
                node("""
                        [{"pdno":"005930","prdt_name":"삼성전자","hldg_qty":"10","pchs_avg_pric":"70000.0000","prpr":"76800",
                          "pchs_amt":"700000","evlu_amt":"768000","evlu_pfls_amt":"68000","evlu_pfls_rt":"9.71"},
                         {"pdno":"000660","prdt_name":"SK하이닉스","hldg_qty":"0","pchs_avg_pric":"0","prpr":"183500",
                          "pchs_amt":"0","evlu_amt":"0","evlu_pfls_amt":"0","evlu_pfls_rt":"0.00"}]"""),
                node("""
                        [{"dnca_tot_amt":"1000000","prvs_rcdl_excc_amt":"950000","pchs_amt_smtl_amt":"700000",
                          "evlu_amt_smtl_amt":"768000","evlu_pfls_smtl_amt":"68000","tot_evlu_amt":"1718000","nass_amt":"1718000"}]"""));
        var balance = controller(source(response)).balance(me).block();

        assertEquals(myAccount, usedAccount.get());
        assertEquals("REAL", balance.environment());
        assertEquals("••••5678", balance.accountNumber());
        assertEquals(1, balance.holdings().size());
        assertEquals("삼성전자", balance.holdings().get(0).name());
        assertEquals(0, new BigDecimal("70000").compareTo(balance.holdings().get(0).averagePrice()));
        assertEquals(0, new BigDecimal("1718000").compareTo(balance.totalEvaluation()));
        assertEquals(0, new BigDecimal("950000").compareTo(balance.settlementDeposit()));
        assertEquals(new BigDecimal("9.71"), balance.profitRate());
    }

    @Test
    void emptyAccountHasNoProfitRate() {
        var response = new KisApiResponse("0", "", "", null, node("[]"),
                node("[{\"pchs_amt_smtl_amt\":\"0\",\"evlu_pfls_smtl_amt\":\"0\",\"tot_evlu_amt\":\"500\"}]"));
        var balance = controller(source(response)).balance(me).block();
        assertEquals(0, balance.holdings().size());
        assertNull(balance.profitRate());
    }

    @Test
    void kisErrorBecomesBadGateway() {
        var response = new KisApiResponse("1", "EGW00123", "기간이 만료된 token 입니다.", null, null, null);
        var error = assertThrows(ResponseStatusException.class,
                () -> controller(source(response)).balance(me).block());
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void userWithoutRegisteredCredentialsGetsNotFoundAndKisIsNotCalled() {
        var other = new TestingAuthenticationToken("github:2", null, "ROLE_USER");
        var error = assertThrows(ResponseStatusException.class,
                () -> controller(source(new KisApiResponse("0", "", "", null, null, null))).balance(other).block());
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertNull(usedAccount.get());
    }

    @Test
    void missingAccountConfigurationBecomesServiceUnavailable() {
        AccountBalanceSource source = () -> { throw new IllegalStateException("Set account-number"); };
        var error = assertThrows(ResponseStatusException.class,
                () -> controller(source).balance(me).block());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
