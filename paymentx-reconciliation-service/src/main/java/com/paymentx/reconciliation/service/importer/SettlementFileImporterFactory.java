package com.paymentx.reconciliation.service.importer;

import com.paymentx.reconciliation.entity.SettlementFileType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Matches NotificationChannelFactory's exact auto-discovery pattern -
 * adding an XmlSettlementFileImporter later requires zero changes here.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementFileImporterFactory is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.importer and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementFileImporterFactory PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.service.importer package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SettlementFileImporterFactory {

    private final Map<SettlementFileType, SettlementFileImporter> importers;

    public SettlementFileImporterFactory(List<SettlementFileImporter> settlementFileImporters) {
        this.importers = new EnumMap<>(SettlementFileType.class);
        for (SettlementFileImporter importer : settlementFileImporters) {
            importers.put(importer.getSupportedType(), importer);
        }
    }

    public Optional<SettlementFileImporter> getImporter(SettlementFileType fileType) {
        return Optional.ofNullable(importers.get(fileType));
    }
}
