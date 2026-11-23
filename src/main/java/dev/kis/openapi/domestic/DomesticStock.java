package dev.kis.openapi.domestic;

/** A domestic stock entry from the KIS security master files. */
public record DomesticStock(String code, String standardCode, String name, Market market) {
    public enum Market { KOSPI, KOSDAQ }
}
