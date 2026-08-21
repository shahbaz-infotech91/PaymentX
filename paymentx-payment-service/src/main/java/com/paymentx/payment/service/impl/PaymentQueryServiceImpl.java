package com.paymentx.payment.service.impl;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.payment.dto.PaymentResponse;
import com.paymentx.payment.dto.PaymentSearchCriteria;
import com.paymentx.payment.dto.PaymentStatusResponse;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentStatus;
import com.paymentx.payment.mapper.PaymentMapper;
import com.paymentx.payment.repository.PaymentRepository;
import com.paymentx.payment.repository.PaymentSpecifications;
import com.paymentx.payment.service.PaymentQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional(readOnly = true) at the class level: every method here
 * is a pure read. readOnly=true lets Hibernate skip dirty-checking
 * overhead and lets the underlying connection pool/DB potentially route
 * to a read replica in the future - a correctness-neutral, free
 * optimization for a query-only service.
 */
@Service
@Transactional(readOnly = true)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentQueryServiceImpl is a service in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentQueryServiceImpl PaymentX ke payment module ka ek service hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    public PaymentQueryServiceImpl(PaymentRepository paymentRepository, PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public PaymentResponse getByReference(String paymentReference) {
        return paymentMapper.toResponse(findOrThrow(paymentReference));
    }

    @Override
    public PaymentStatusResponse getStatus(String paymentReference) {
        return paymentMapper.toStatusResponse(findOrThrow(paymentReference));
    }

    @Override
    public PageResponse<PaymentResponse> list(PaymentStatus status, int page, int size) {
        Specification<Payment> spec = Specification.allOf(PaymentSpecifications.hasStatus(status));

        var pageResult = paymentRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.of(
                pageResult.getContent().stream().map(paymentMapper::toResponse).toList(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements());
    }

    @Override
    public PageResponse<PaymentResponse> search(PaymentSearchCriteria criteria, int page, int size) {
        Specification<Payment> spec = Specification
                .allOf(PaymentSpecifications.hasStatus(criteria.status()))
                .and(PaymentSpecifications.hasScheme(criteria.scheme()))
                .and(PaymentSpecifications.involvesParticipant(criteria.participantId()))
                .and(PaymentSpecifications.createdAfter(criteria.createdFrom()))
                .and(PaymentSpecifications.createdBefore(criteria.createdTo()))
                .and(PaymentSpecifications.amountAtLeast(criteria.minAmount()))
                .and(PaymentSpecifications.amountAtMost(criteria.maxAmount()));

        var pageResult = paymentRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.of(
                pageResult.getContent().stream().map(paymentMapper::toResponse).toList(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements());
    }

    private Payment findOrThrow(String paymentReference) {
        return paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentReference));
    }
}
