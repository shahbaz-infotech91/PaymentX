package com.paymentx.reconciliation.service.importer;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.entity.SettlementFileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * WHY OpenCSV's CSVReader (row-by-row iterator), not a
 * readAll()-into-List API: same streaming rationale as the
 * SettlementFileImporter interface itself - CSVReader.readNext() pulls
 * one row at a time from the underlying stream, never buffering the
 * whole file.
 *
 * Expected column order: paymentId, referenceId, participantId, amount,
 * currency, status, settlementDate (ISO-8601). A header row is expected
 * and skipped.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CsvSettlementFileImporter is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.importer and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CsvSettlementFileImporter PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.service.importer package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class CsvSettlementFileImporter implements SettlementFileImporter {

    @Override
    public SettlementFileType getSupportedType() {
        return SettlementFileType.CSV;
    }

    @Override
    public Stream<ExternalSettlementRecord> parse(InputStream inputStream) throws SettlementFileParseException {
        try {
            CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            csvReader.readNext(); // skip header row

            Iterator<ExternalSettlementRecord> iterator = new Iterator<>() {
                private String[] nextRow = readNextRow();

                @Override
                public boolean hasNext() {
                    return nextRow != null;
                }

                @Override
                public ExternalSettlementRecord next() {
                    if (nextRow == null) {
                        throw new NoSuchElementException();
                    }
                    ExternalSettlementRecord record = toRecord(nextRow);
                    nextRow = readNextRow();
                    return record;
                }

                private String[] readNextRow() {
                    try {
                        return csvReader.readNext();
                    } catch (CsvValidationException | IOException e) {
                        throw new IllegalStateException("Failed to read CSV row", e);
                    }
                }
            };

            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                    .onClose(() -> {
                        try {
                            csvReader.close();
                        } catch (IOException e) {
                            log.warn("Failed to close CSV reader", e);
                        }
                    });

        } catch (IOException | CsvValidationException e) {
            throw new SettlementFileParseException("Failed to parse CSV settlement file: " + e.getMessage(), e);
        }
    }

    private ExternalSettlementRecord toRecord(String[] row) {
        if (row.length < 7) {
            throw new IllegalStateException("CSV row has fewer than 7 expected columns: " + row.length);
        }
        return new ExternalSettlementRecord(
                row[0].trim(),
                row[1].trim(),
                row[2].trim(),
                new BigDecimal(row[3].trim()),
                row[4].trim(),
                row[5].trim(),
                OffsetDateTime.parse(row[6].trim())
        );
    }
}
