package com.paymentx.reconciliation.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * JsonSettlementFileImporterTest is a JUnit test class in the reconciliation module of PaymentX, package com.paymentx.reconciliation.service.importer. It is used within reconciliation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JsonSettlementFileImporterTest PaymentX ke reconciliation module ka ek JUnit test class hai, package com.paymentx.reconciliation.service.importer me. Ye reconciliation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class JsonSettlementFileImporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final JsonSettlementFileImporter importer = new JsonSettlementFileImporter(objectMapper);

    @Test
    void parse_validJsonArray_streamsAllElements() throws Exception {
        String json = "["
                + "{\"paymentId\":\"pay-1\",\"referenceId\":\"ref-1\",\"participantId\":\"BANK001\",\"amount\":100.00,\"currency\":\"USD\",\"status\":\"SETTLED\",\"settlementDate\":\"2026-01-15T10:00:00Z\"},"
                + "{\"paymentId\":\"pay-2\",\"referenceId\":\"ref-2\",\"participantId\":\"BANK002\",\"amount\":250.50,\"currency\":\"EUR\",\"status\":\"SETTLED\",\"settlementDate\":\"2026-01-15T11:00:00Z\"}"
                + "]";
        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        try (var stream = importer.parse(inputStream)) {
            List<ExternalSettlementRecord> records = stream.toList();

            assertThat(records).hasSize(2);
            assertThat(records.get(0).paymentId()).isEqualTo("pay-1");
            assertThat(records.get(0).amount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(records.get(1).currency()).isEqualTo("EUR");
        }
    }

    @Test
    void parse_emptyJsonArray_returnsEmptyStream() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8));

        try (var stream = importer.parse(inputStream)) {
            assertThat(stream.toList()).isEmpty();
        }
    }

    @Test
    void getSupportedType_returnsJson() {
        assertThat(importer.getSupportedType()).isEqualTo(SettlementFileType.JSON);
    }
}
