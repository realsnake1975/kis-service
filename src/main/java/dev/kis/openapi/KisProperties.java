package dev.kis.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("kis.openapi")
@Getter
@Setter
public class KisProperties {
    private KisEnvironment environment = KisEnvironment.PAPER;
    private final Account paper = new Account();
    private final Account real = new Account();
    private final DomesticStockMonitor domesticStockMonitor = new DomesticStockMonitor();

    public Account account(KisEnvironment selected) {
        return selected == KisEnvironment.REAL ? real : paper;
    }

    @Getter
    @Setter
    public static class Account {
        private String accountNumber;
        private String accountProductCode = "01";

    }

    @Getter
    @Setter
    public static class DomesticStockMonitor {
        private boolean enabled;
        private Duration interval = Duration.ofMinutes(1);
        private List<String> symbols = new ArrayList<>();
        private final Persistence persistence = new Persistence();

        public void setSymbols(List<String> symbols) { this.symbols = symbols == null ? new ArrayList<>() : new ArrayList<>(symbols); }
    }

    @Getter
    @Setter
    public static class Persistence {
        private boolean enabled;
    }
}
