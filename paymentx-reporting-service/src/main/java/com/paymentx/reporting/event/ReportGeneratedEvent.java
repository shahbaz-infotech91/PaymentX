package com.paymentx.reporting.event;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Published when a report execution completes successfully -
 * lets downstream consumers know fresh data is ready without polling
 * the REST API.
 *
 * HINGLISH: Jab ek report execution successfully complete hota hai tab
 * publish hota hai - downstream consumers ko REST API poll kiye bina
 * pata chal jaata hai ki fresh data ready hai.
 * ====================================================================
 */
@Getter
@Builder
public class ReportGeneratedEvent {
    private UUID reportExecutionId;
    private String reportType;
    private Integer rowCount;
}
