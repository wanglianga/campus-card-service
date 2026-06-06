package com.campus.card.dto

import com.campus.card.model.*
import java.math.BigDecimal
import java.time.LocalDateTime

data class CardResponse(
    val id: Long,
    val cardNo: String,
    val studentId: String,
    val studentName: String,
    val status: CardStatus,
    val balance: BigDecimal,
    val frozenAmount: BigDecimal,
    val createdAt: LocalDateTime,
    val lastActiveAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val previousCardId: Long?
)

data class LostReportResponse(
    val id: Long,
    val cardId: Long,
    val cardNo: String,
    val studentId: String,
    val studentName: String,
    val channel: LostChannel,
    val reporterContact: String?,
    val status: LostReportStatus,
    val reportedAt: LocalDateTime,
    val confirmedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val cancelReason: String?,
    val cancelledByMisreport: Boolean,
    val operatorId: String?,
    val remark: String?
)

data class ReissueResponse(
    val id: Long,
    val lostReportId: Long?,
    val oldCardId: Long,
    val oldCardNo: String,
    val newCardId: Long?,
    val newCardNo: String?,
    val studentId: String,
    val studentName: String,
    val status: ReissueStatus,
    val reissueFee: BigDecimal,
    val feePaid: Boolean,
    val feePaidAt: LocalDateTime?,
    val identityVerifiedAt: LocalDateTime?,
    val verifierId: String?,
    val batchId: Long?,
    val pickupLocation: String,
    val requestedAt: LocalDateTime,
    val cardReadyAt: LocalDateTime?,
    val pickedUpAt: LocalDateTime?,
    val receiverId: String?,
    val balanceTransferred: Boolean,
    val balanceTransferredAt: LocalDateTime?,
    val oldCardCancelled: Boolean,
    val oldCardCancelledAt: LocalDateTime?,
    val failureReason: String?,
    val failedAt: LocalDateTime?
)

data class CardBatchResponse(
    val id: Long,
    val batchNo: String,
    val createdAt: LocalDateTime,
    val totalCards: Int,
    val finishedCards: Int,
    val operatorId: String,
    val remark: String?
)

data class ConsumptionResponse(
    val id: Long,
    val cardId: Long,
    val cardNo: String,
    val studentId: String,
    val terminalId: String,
    val terminalName: String,
    val location: String,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val consumedAt: LocalDateTime,
    val syncedAt: LocalDateTime?,
    val isUnsynced: Boolean,
    val status: ConsumptionStatus,
    val remark: String?
)

data class DisputeResponse(
    val id: Long,
    val consumptionId: Long?,
    val cardId: Long,
    val cardNo: String,
    val studentId: String,
    val disputeType: DisputeType,
    val disputedAmount: BigDecimal,
    val description: String,
    val evidence: String?,
    val status: DisputeStatus,
    val terminalId: String?,
    val submittedAt: LocalDateTime,
    val reviewedAt: LocalDateTime?,
    val reviewerId: String?,
    val reviewNote: String?,
    val refundAmount: BigDecimal?,
    val refundedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
    val rejectReason: String?
)

data class BlacklistEntry(
    val cardNo: String,
    val studentId: String,
    val studentName: String,
    val reason: String,
    val effectiveAt: LocalDateTime
)

data class CardOperationResponse(
    val id: Long,
    val cardId: Long,
    val cardNo: String,
    val operationType: String,
    val beforeStatus: CardStatus?,
    val afterStatus: CardStatus?,
    val operatorId: String?,
    val operatedAt: LocalDateTime,
    val detail: String?
)

data class ApiError(
    val code: String,
    val message: String,
    val details: String? = null
)
