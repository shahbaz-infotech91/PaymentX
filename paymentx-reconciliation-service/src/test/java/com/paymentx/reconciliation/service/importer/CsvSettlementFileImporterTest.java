package com.paymentx.reconciliation.service.importer;

import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.entity.SettlementFileType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CsvSettlementFileImporterTest is a JUnit test class in the reconciliation module of PaymentX, package com.paymentx.reconciliation.service.importer. It is used within reconciliation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CsvSettlementFileImporterTest PaymentX ke reconciliation module ka ek JUnit test class hai, package com.paymentx.reconciliation.service.importer me. Ye reconciliation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class CsvSettlementFileImporterTest {

    private final CsvSettlementFileImporter importer = new CsvSettlementFileImporter();

    @Test
    void parse_validCsv_streamsAllRowsExcludingHeader() throws Exception {
        String csv = "paymentId,referenceId,participantId,amount,currency,status,settlementDate\n"
                + "pay-1,ref-1,BANK001,100.00,USD,SETTLED,2026-01-15T10:00:00Z\n"
                + "pay-2,ref-2,BANK002,250.50,EUR,SETTLED,2026-01-15T11:00:00Z\n";
        InputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        try (var stream = importer.parse(inputStream)) {
            List<ExternalSettlementRecord> records = stream.toList();

            assertThat(records).hasSize(2);
            assertThat(records.get(0).paymentId()).isEqualTo("pay-1");
            assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(records.get(1).currency()).isEqualTo("EUR");
        }
    }

    @Test
    void parse_emptyFileWithOnlyHeader_returnsEmptyStream() throws Exception {
        String csv = "paymentId,referenceId,participantId,amount,currency,status,settlementDate\n";
        InputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        try (var stream = importer.parse(inputStream)) {
            assertThat(stream.toList()).isEmpty();
        }
    }

    @Test
    void getSupportedType_returnsCsv() {
        assertThat(importer.getSupportedType()).isEqualTo(SettlementFileType.CSV);
    }
}
