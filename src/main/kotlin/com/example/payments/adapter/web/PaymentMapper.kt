package com.example.payments.adapter.web

import com.example.payments.adapter.web.model.PaymentPage
import com.example.payments.application.port.PagedResult
import com.example.payments.domain.Payment
import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.example.payments.adapter.web.model.Payment as PaymentDto
import com.example.payments.adapter.web.model.PaymentStatus as PaymentStatusDto
import com.example.payments.domain.PaymentStatus as DomainStatus

fun Payment.toDto() = PaymentDto(
    id = id,
    amount = money.amountMinor,
    currency = money.currency,
    reference = reference,
    status = PaymentStatusDto.valueOf(status.name),
    createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
    updatedAt = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
)

fun PagedResult<Payment>.toDto(limit: Int, offset: Int) = PaymentPage(
    items = items.map { it.toDto() },
    limit = limit,
    offset = offset,
    total = total,
)

fun PaymentStatusDto.toDomain(): DomainStatus = DomainStatus.valueOf(name)
