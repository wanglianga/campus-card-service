package com.campus.card.service

import com.campus.card.dto.*
import com.campus.card.exception.*
import com.campus.card.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

class DisputeService(
    private val cardService: CardService,
    private val consumptionService: ConsumptionService
) {

    fun submitDispute(request: SubmitDisputeRequest): DisputeResponse = transaction {
        val cardRow = Cards.select { Cards.cardNo eq request.cardNo }.firstOrNull()
            ?: throw CardNotFoundException(request.cardNo)

        if (cardRow[Cards.studentId] != request.studentId) {
            throw StudentMismatchException(request.cardNo, request.studentId)
        }

        val now = LocalDateTime.now()

        val consumptionRow = request.consumptionId?.let {
            Consumptions.select { Consumptions.id eq it }.firstOrNull()
        }

        val id = Disputes.insertAndGetId {
            it[consumptionId] = request.consumptionId
            it[cardId] = cardRow[Cards.id].value
            it[cardNo] = request.cardNo
            it[studentId] = request.studentId
            it[disputeType] = request.disputeType
            it[disputedAmount] = request.disputedAmount
            it[description] = request.description
            it[evidence] = request.evidence
            it[status] = DisputeStatus.SUBMITTED
            it[terminalId] = request.terminalId ?: consumptionRow?.get(Consumptions.terminalId)
            it[submittedAt] = now
        }.value

        if (consumptionRow != null) {
            Consumptions.update({ Consumptions.id eq request.consumptionId!! }) {
                it[status] = ConsumptionStatus.DISPUTED
            }
        }

        logger.info { "Dispute submitted: card=${request.cardNo}, type=${request.disputeType}, amount=${request.disputedAmount}, disputeId=$id" }
        getDisputeById(id)!!
    }

    fun reviewDispute(disputeId: Long, request: ReviewDisputeRequest): DisputeResponse = transaction {
        val row = Disputes.select { Disputes.id eq disputeId }.firstOrNull()
            ?: throw DisputeNotFoundException(disputeId)

        if (row[Disputes.status] != DisputeStatus.SUBMITTED && row[Disputes.status] != DisputeStatus.UNDER_REVIEW) {
            throw InvalidStateException(
                "CANNOT_REVIEW",
                "当前争议状态 ${row[Disputes.status]} 不可审核",
                "只有 SUBMITTED 或 UNDER_REVIEW 状态可审核"
            )
        }

        val now = LocalDateTime.now()

        if (request.approved) {
            val refundAmount = request.refundAmount ?: row[Disputes.disputedAmount]

            Disputes.update({ Disputes.id eq disputeId }) {
                it[status] = DisputeStatus.REFUND_APPROVED
                it[reviewedAt] = now
                it[reviewerId] = request.reviewerId
                it[reviewNote] = request.reviewNote
                it[refundAmount] = refundAmount
            }

            val cardRow = Cards.select { Cards.id eq row[Disputes.cardId] }.firstOrNull()
            if (cardRow != null) {
                Cards.update({ Cards.id eq row[Disputes.cardId] }) {
                    it[balance] = cardRow[Cards.balance].add(refundAmount)
                }
                cardService.recordOperation(
                    row[Disputes.cardId],
                    row[Disputes.cardNo],
                    "DISPUTE_REFUND",
                    cardRow[Cards.status],
                    cardRow[Cards.status],
                    request.reviewerId,
                    "争议退款: $refundAmount，争议类型: ${row[Disputes.disputeType]}, 审核备注: ${request.reviewNote}"
                )
            }

            if (row[Disputes.consumptionId] != null) {
                Consumptions.update({ Consumptions.id eq row[Disputes.consumptionId]!! }) {
                    it[status] = ConsumptionStatus.REFUNDED
                }
            }

            Disputes.update({ Disputes.id eq disputeId }) {
                it[status] = DisputeStatus.REFUND_PROCESSED
                it[refundedAt] = LocalDateTime.now()
            }

            Disputes.update({ Disputes.id eq disputeId }) {
                it[status] = DisputeStatus.CLOSED
                it[closedAt] = LocalDateTime.now()
            }

            logger.info { "Dispute approved and refunded: disputeId=$disputeId, refund=$refundAmount" }
        } else {
            Disputes.update({ Disputes.id eq disputeId }) {
                it[status] = DisputeStatus.REJECTED
                it[reviewedAt] = now
                it[reviewerId] = request.reviewerId
                it[reviewNote] = request.reviewNote
                it[rejectReason] = request.rejectReason
            }
            Disputes.update({ Disputes.id eq disputeId }) {
                it[status] = DisputeStatus.CLOSED
                it[closedAt] = LocalDateTime.now()
            }
            logger.info { "Dispute rejected: disputeId=$disputeId, reason=${request.rejectReason}" }
        }

        getDisputeById(disputeId)!!
    }

    fun getDispute(disputeId: Long): DisputeResponse = transaction {
        getDisputeById(disputeId) ?: throw DisputeNotFoundException(disputeId)
    }

    fun listDisputes(
        status: DisputeStatus? = null,
        studentId: String? = null,
        disputeType: DisputeType? = null,
        cardNo: String? = null
    ): List<DisputeResponse> = transaction {
        var query = Disputes.selectAll()
        if (status != null) query = query.andWhere { Disputes.status eq status }
        if (studentId != null) query = query.andWhere { Disputes.studentId eq studentId }
        if (disputeType != null) query = query.andWhere { Disputes.disputeType eq disputeType }
        if (cardNo != null) query = query.andWhere { Disputes.cardNo eq cardNo }
        query.orderBy(Disputes.submittedAt, SortOrder.DESC).map { toDisputeResponse(it) }
    }

    private fun getDisputeById(id: Long): DisputeResponse? = transaction {
        Disputes.select { Disputes.id eq id }.firstOrNull()?.let { toDisputeResponse(it) }
    }

    internal fun toDisputeResponse(row: ResultRow): DisputeResponse = DisputeResponse(
        id = row[Disputes.id].value,
        consumptionId = row[Disputes.consumptionId],
        cardId = row[Disputes.cardId],
        cardNo = row[Disputes.cardNo],
        studentId = row[Disputes.studentId],
        disputeType = row[Disputes.disputeType],
        disputedAmount = row[Disputes.disputedAmount],
        description = row[Disputes.description],
        evidence = row[Disputes.evidence],
        status = row[Disputes.status],
        terminalId = row[Disputes.terminalId],
        submittedAt = row[Disputes.submittedAt],
        reviewedAt = row[Disputes.reviewedAt],
        reviewerId = row[Disputes.reviewerId],
        reviewNote = row[Disputes.reviewNote],
        refundAmount = row[Disputes.refundAmount],
        refundedAt = row[Disputes.refundedAt],
        closedAt = row[Disputes.closedAt],
        rejectReason = row[Disputes.rejectReason]
    )
}
