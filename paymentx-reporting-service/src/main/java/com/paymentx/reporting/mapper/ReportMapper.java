package com.paymentx.reporting.mapper;

import com.paymentx.reporting.dto.ReportExecutionResponse;
import com.paymentx.reporting.dto.ReportResponse;
import com.paymentx.reporting.entity.Report;
import com.paymentx.reporting.entity.ReportExecution;
import org.mapstruct.Mapper;

/**
 * ====================================================================
 * ENGLISH: MapStruct mapper - entity to REST DTO conversion. Kept
 * intentionally thin (no custom logic) since every field maps
 * one-to-one; complex JSON-result formatting lives in ReportExporter
 * implementations instead, not here.
 *
 * HINGLISH: MapStruct mapper - entity ko REST DTO me convert karta hai.
 * Jaan-boojh kar thin rakha hai kyunki har field one-to-one map hoti
 * hai; complex JSON-result formatting ReportExporter implementations
 * me hoti hai, yahan nahi.
 * ====================================================================
 */
@Mapper(componentModel = "spring")
public interface ReportMapper {

    ReportResponse toResponse(Report entity);

    ReportExecutionResponse toResponse(ReportExecution entity);
}
