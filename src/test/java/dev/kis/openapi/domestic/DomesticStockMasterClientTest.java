package dev.kis.openapi.domestic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomesticStockMasterClientTest {
    private static final Charset MS949 = Charset.forName("MS949");

    @Test
    void parsesKospiMasterCodeStandardCodeAndKoreanName() {
        byte[] line = masterLine("005930", "KR7005930003", "삼성전자", 228).getBytes(MS949);

        List<DomesticStock> stocks = DomesticStockMasterClient.parseMaster(line, DomesticStock.Market.KOSPI);

        assertEquals(List.of(new DomesticStock("005930", "KR7005930003", "삼성전자", DomesticStock.Market.KOSPI)), stocks);
    }

    @Test
    void parsesKosdaqUsingItsOwnFixedWidthTail() {
        byte[] line = masterLine("035420", "KR7035420009", "NAVER", 222).getBytes(MS949);

        List<DomesticStock> stocks = DomesticStockMasterClient.parseMaster(line, DomesticStock.Market.KOSDAQ);

        assertEquals("035420", stocks.get(0).code());
        assertEquals("KR7035420009", stocks.get(0).standardCode());
        assertEquals("NAVER", stocks.get(0).name());
        assertEquals(DomesticStock.Market.KOSDAQ, stocks.get(0).market());
    }

    @Test
    void ignoresMalformedAndNonStockCodes() {
        String invalidCode = "ABCDEF   " + "KR7005930003" + "삼성전자" + " ".repeat(228);
        String blankName = "000001   " + "KR7000000001" + " ".repeat(229);

        List<DomesticStock> stocks = DomesticStockMasterClient.parseMaster(
                (invalidCode + "\n" + blankName).getBytes(MS949), DomesticStock.Market.KOSPI);

        assertTrue(stocks.isEmpty());
    }

    @Test
    void searchesByPartialNameAndExactCodeAndReturnsAllWhenUnfiltered() {
        List<DomesticStock> stocks = List.of(
                new DomesticStock("005930", "KR7005930003", "삼성전자", DomesticStock.Market.KOSPI),
                new DomesticStock("005935", "KR7005931001", "삼성전자우", DomesticStock.Market.KOSPI),
                new DomesticStock("000660", "KR7000660001", "SK하이닉스", DomesticStock.Market.KOSPI));

        assertEquals(3, DomesticStockMasterClient.filter(stocks, null, null).size());
        assertEquals(List.of("005930", "005935"), DomesticStockMasterClient.filter(stocks, "삼성", null)
                .stream().map(DomesticStock::code).toList());
        assertEquals(List.of("005935"), DomesticStockMasterClient.filter(stocks, "전자", "005935")
                .stream().map(DomesticStock::code).toList());
        assertEquals(List.of("000660"), DomesticStockMasterClient.filter(stocks, " ", " 000660 ")
                .stream().map(DomesticStock::code).toList());
    }

    @Test
    void extractsFirstFileFromZipAndRejectsAnEmptyArchive() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("master.mst"));
            zip.write("master data".getBytes(MS949));
            zip.closeEntry();
        }

        assertEquals("master data", new String(DomesticStockMasterClient.unzip(bytes.toByteArray()), MS949));
        assertThrows(IllegalArgumentException.class, () -> DomesticStockMasterClient.unzip(new byte[0]));
    }

    private String masterLine(String code, String standardCode, String name, int trailingCharacters) {
        return String.format("%-9s%-12s%s%s", code, standardCode, name, " ".repeat(trailingCharacters));
    }
}
