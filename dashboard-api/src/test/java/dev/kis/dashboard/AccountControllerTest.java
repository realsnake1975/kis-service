package dev.kis.dashboard;

import dev.kis.openapi.KisApiResponse;
import dev.kis.openapi.KisEnvironment;
import dev.kis.openapi.KisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountControllerTest {
    private final JsonMapper json = JsonMapper.builder().build();

    private KisProperties properties() {
        KisProperties properties = new KisProperties();
        properties.setEnvironment(KisEnvironment.REAL);
        properties.getReal().setAccountNumber("12345678");
        return properties;
    }

    private JsonNode node(String value) { return json.readTree(value); }

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
        var controller = new AccountController(() -> Mono.just(response), properties());

        var balance = controller.balance().block();

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
        var balance = new AccountController(() -> Mono.just(response), properties()).balance().block();
        assertEquals(0, balance.holdings().size());
        assertNull(balance.profitRate());
    }

    @Test
    void kisErrorBecomesBadGateway() {
        var response = new KisApiResponse("1", "EGW00123", "기간이 만료된 token 입니다.", null, null, null);
        var error = assertThrows(ResponseStatusException.class,
                () -> new AccountController(() -> Mono.just(response), properties()).balance().block());
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void missingAccountConfigurationBecomesServiceUnavailable() {
        AccountBalanceSource source = () -> { throw new IllegalStateException("Set account-number"); };
        var error = assertThrows(ResponseStatusException.class,
                () -> new AccountController(source, properties()).balance().block());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
