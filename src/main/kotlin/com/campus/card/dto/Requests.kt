package com.campus.card.dto

import com.campus.card.model.*
import java.math.BigDecimal

data class CreateCardRequest(
    val cardNo: String,
    val studentId: String,
    val studentName: String,
    val initialBalance: BigDecimal = BigDecimal.ZERO
)

data class SubmitLostReportRequest(
    val cardNo: String,
    val studentId: String,
    val channel: LostChannel,
    val reporterContact: String? = null,
    val remark: String? = null
)

data class CancelLostReportRequest(
    val cancelReason: String,
    val isMisreport: Boolean = false,
    val operatorId: String? = null
)

data class RequestReissueRequest(
    val lostReportId: Long? = null,
    val oldCardNo: String,
    val studentId: String,
    val pickupLocation: String? = null,
    val operatorId: String? = null
)

data class VerifyIdentityRequest(
    val verifierId: String,
    val verified: Boolean,
    val note: String? = null
)

data class PayFeeRequest(
    val paidAmount: BigDecimal
)

data class AssignBatchRequest(
    val batchId: Long
)

data class CreateBatchRequest(
    val batchNo: String,
    val operatorId: String,
    val remark: String? = null
)

data class MarkCardReadyRequest(
    val newCardNo: String
)

data class PickupCardRequest(
    val receiverId: String
)

data class FailReissueRequest(
    val failureReason: String
)

data class SubmitDisputeRequest(
    val cardNo: String,
    val studentId: String,
    val disputeType: DisputeType,
    val disputedAmount: BigDecimal,
    val description: String,
    val evidence: String? = null,
    val terminalId: String? = null,
    val consumptionId: Long? = null
)

data class ReviewDisputeRequest(
    val reviewerId: String,
    val approved: Boolean,
    val reviewNote: String,
    val refundAmount: BigDecimal? = null,
    val rejectReason: String? = null
)

data class RecordConsumptionRequest(
    val cardNo: String,
    val terminalId: String,
    val terminalName: String,
    val location: String,
    val amount: BigDecimal,
    val consumedAt: String? = null,
    val isUnsynced: Boolean = false
)

data class SyncConsumptionRequest(
    val consumptionIds: List<Long>
)
