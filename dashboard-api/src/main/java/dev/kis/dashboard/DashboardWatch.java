package dev.kis.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kis_dashboard_watch")
public class DashboardWatch {
    @Id
    @Column(name = "stock_code", nullable = false, length = 6)
    private String code;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String name;

    @Column(name = "market", nullable = false, length = 10)
    private String market;

    protected DashboardWatch() { }

    public DashboardWatch(String code, String name, String market) {
        this.code = code;
        this.name = name;
        this.market = market;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getMarket() { return market; }
}
