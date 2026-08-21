package com.paymentx.reconciliation.service.importer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.entity.SettlementFileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * WHY ObjectMapper.readValues() (returns a lazy MappingIterator), not
 * readValue() into a List<ExternalSettlementRecord>: Jackson's streaming
 * parser reads and deserializes one array element at a time as the
 * caller advances the iterator - the same constant-memory streaming
 * guarantee as CsvSettlementFileImporter, for a JSON array file of
 * arbitrary size.
 *
 * Expected format: a top-level JSON array of objects with field names
 * matching ExternalSettlementRecord's record components exactly.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * JsonSettlementFileImporter is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.importer and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * JsonSettlementFileImporter PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.service.importer package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class JsonSettlementFileImporter implements SettlementFileImporter {

    private final ObjectMapper objectMapper;

    public JsonSettlementFileImporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SettlementFileType getSupportedType() {
        return SettlementFileType.JSON;
    }

    @Override
    public Stream<ExternalSettlementRecord> parse(InputStream inputStream) throws SettlementFileParseException {
        try {
            JsonParser parser = objectMapper.getFactory().createParser(inputStream);
            // readValues(JsonParser) requires the parser positioned AT the
            // first element's own token, not at the wrapping START_ARRAY -
            // per ObjectReader's javadoc, "parser MUST NOT point to the
            // surrounding START_ARRAY but rather to the token following
            // it". Without consuming both the array start and moving past
            // it here, Jackson tries to deserialize the whole array as a
            // single ExternalSettlementRecord and fails with a "from Array
            // value" MismatchedInputException.
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new SettlementFileParseException("Expected a top-level JSON array of settlement records");
            }
            if (parser.nextToken() == JsonToken.END_ARRAY) {
                // Empty array - no first element to position readValues()
                // at, and calling it anyway makes Jackson try to
                // deserialize END_ARRAY itself as a record.
                parser.close();
                return Stream.empty();
            }
            MappingIterator<ExternalSettlementRecord> mappingIterator =
                    objectMapper.readValues(parser, ExternalSettlementRecord.class);

            Iterator<ExternalSettlementRecord> iterator = new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return mappingIterator.hasNext();
                }

                @Override
                public ExternalSettlementRecord next() {
                    return mappingIterator.next();
                }
            };

            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                    .onClose(() -> {
                        try {
                            mappingIterator.close();
                        } catch (IOException e) {
                            log.warn("Failed to close JSON mapping iterator", e);
                        }
                    });

        } catch (IOException e) {
            throw new SettlementFileParseException("Failed to parse JSON settlement file: " + e.getMessage(), e);
        }
    }
}
