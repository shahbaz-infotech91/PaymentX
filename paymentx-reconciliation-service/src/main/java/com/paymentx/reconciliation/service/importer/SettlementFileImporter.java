package com.paymentx.reconciliation.service.importer;

import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.entity.SettlementFileType;

import java.io.InputStream;
import java.util.stream.Stream;

/**
 * WHY InputStream, not MultipartFile: MultipartFile couples this
 * interface to Spring MVC's request-scoped multipart handling, but the
 * actual bytes being parsed here come from SettlementFile.fileContent
 * (durably persisted at upload time - see that entity's javadoc for why
 * MultipartFile itself cannot cross the async batch-processing
 * boundary). InputStream is the correct, framework-agnostic abstraction
 * for "a stream of bytes to parse," regardless of where those bytes
 * originated.
 *
 * WHY Stream<ExternalSettlementRecord>, not List: "Streaming" and "Large
 * file support" are explicit requirements - a settlement file with
 * millions of rows must never be fully materialized in memory as a List.
 *
 * WHY this is a Strategy interface, not a switch on file extension:
 * "Future-ready XML" and "Support future settlement providers" are
 * explicit requirements - adding an XML importer later means writing
 * one new @Component with zero changes to
 * SettlementFileImporterFactory or any calling code.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementFileImporter is a interface in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.importer and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementFileImporter PaymentX ke reconciliation module ka ek interface hai. Ye com.paymentx.reconciliation.service.importer package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface SettlementFileImporter {

    SettlementFileType getSupportedType();

    Stream<ExternalSettlementRecord> parse(InputStream inputStream) throws SettlementFileParseException;
}
