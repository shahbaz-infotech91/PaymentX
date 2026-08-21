package com.paymentx.payment.mapper;

import com.paymentx.payment.dto.PaymentResponse;
import com.paymentx.payment.dto.PaymentStatusHistoryItem;
import com.paymentx.payment.dto.PaymentStatusResponse;
import com.paymentx.payment.dto.SettlementResponse;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentSettlement;
import com.paymentx.payment.entity.PaymentStatusHistory;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * WHY componentModel = "spring": generates a @Component-annotated impl,
 * constructor-injectable into the service layer like any other Spring
 * bean - consistent with "constructor injection only" used throughout
 * this project.
 *
 * No explicit @Mapping annotations needed for toResponse/toStatusResponse
 * - Payment's field names already match PaymentResponse's 1:1 (both were
 * designed together in Part 3), so MapStruct's default same-name mapping
 * handles everything, including the nested Money embeddable (mapped
 * as-is since PaymentResponse.amount is typed as the same Money class).
 */
@Mapper(componentModel = "spring")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentMapper is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.mapper and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentMapper PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.mapper package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);

    PaymentStatusResponse toStatusResponse(Payment payment);

    PaymentStatusHistoryItem toHistoryItem(PaymentStatusHistory history);

    List<PaymentStatusHistoryItem> toHistoryItems(List<PaymentStatusHistory> history);

    SettlementResponse toSettlementResponse(PaymentSettlement settlement);
}
