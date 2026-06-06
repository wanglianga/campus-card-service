package com.campus.card.model

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal

object Cards : LongIdTable("cards") {
    val cardNo = varchar("card_no", 20).uniqueIndex()
    val studentId = varchar("student_id", 20).index()
    val studentName = varchar("student_name", 50)
    val status = enumerationByName("status", 20, CardStatus::class)
    val balance = decimal("balance", 12, 2).default(BigDecimal.ZERO)
    val frozenAmount = decimal("frozen_amount", 12, 2).default(BigDecimal.ZERO)
    val createdAt = datetime("created_at")
    val lastActiveAt = datetime("last_active_at").nullable()
    val cancelledAt = datetime("cancelled_at").nullable()
    val previousCardId = long("previous_card_id").nullable()
}

object LostReports : LongIdTable("lost_reports") {
    val cardId = long("card_id").index()
    val cardNo = varchar("card_no", 20)
    val studentId = varchar("student_id", 20)
    val studentName = varchar("student_name", 50)
    val channel = enumerationByName("channel", 30, LostChannel::class)
    val reporterContact = varchar("reporter_contact", 100).nullable()
    val status = enumerationByName("status", 30, LostReportStatus::class)
    val reportedAt = datetime("reported_at")
    val confirmedAt = datetime("confirmed_at").nullable()
    val cancelledAt = datetime("cancelled_at").nullable()
    val cancelReason = varchar("cancel_reason", 200).nullable()
    val cancelledByMisreport = bool("cancelled_by_misreport").default(false)
    val operatorId = varchar("operator_id", 50).nullable()
    val remark = varchar("remark", 500).nullable()
}

object Reissues : LongIdTable("reissues") {
    val lostReportId = long("lost_report_id").nullable().index()
    val oldCardId = long("old_card_id").index()
    val oldCardNo = varchar("old_card_no", 20)
    val newCardId = long("new_card_id").nullable()
    val newCardNo = varchar("new_card_no", 20).nullable()
    val studentId = varchar("student_id", 20)
    val studentName = varchar("student_name", 50)
    val status = enumerationByName("status", 30, ReissueStatus::class)
    val reissueFee = decimal("reissue_fee", 10, 2)
    val feePaid = bool("fee_paid").default(false)
    val feePaidAt = datetime("fee_paid_at").nullable()
    val identityVerifiedAt = datetime("identity_verified_at").nullable()
    val verifierId = varchar("verifier_id", 50).nullable()
    val batchId = long("batch_id").nullable().index()
    val pickupLocation = varchar("pickup_location", 200)
    val requestedAt = datetime("requested_at")
    val cardReadyAt = datetime("card_ready_at").nullable()
    val pickedUpAt = datetime("picked_up_at").nullable()
    val receiverId = varchar("receiver_id", 50).nullable()
    val balanceTransferred = bool("balance_transferred").default(false)
    val balanceTransferredAt = datetime("balance_transferred_at").nullable()
    val oldCardCancelled = bool("old_card_cancelled").default(false)
    val oldCardCancelledAt = datetime("old_card_cancelled_at").nullable()
    val failureReason = varchar("failure_reason", 500).nullable()
    val failedAt = datetime("failed_at").nullable()
}

object CardBatches : LongIdTable("card_batches") {
    val batchNo = varchar("batch_no", 30).uniqueIndex()
    val createdAt = datetime("created_at")
    val totalCards = integer("total_cards").default(0)
    val finishedCards = integer("finished_cards").default(0)
    val operatorId = varchar("operator_id", 50)
    val remark = varchar("remark", 500).nullable()
}

object Consumptions : LongIdTable("consumptions") {
    val cardId = long("card_id").index()
    val cardNo = varchar("card_no", 20)
    val studentId = varchar("student_id", 20)
    val terminalId = varchar("terminal_id", 50)
    val terminalName = varchar("terminal_name", 200)
    val location = varchar("location", 200)
    val amount = decimal("amount", 12, 2)
    val balanceAfter = decimal("balance_after", 12, 2)
    val consumedAt = datetime("consumed_at")
    val syncedAt = datetime("synced_at").nullable()
    val isUnsynced = bool("is_unsynced").default(false)
    val status = enumerationByName("status", 30, ConsumptionStatus::class)
    val remark = varchar("remark", 500).nullable()
}

object Disputes : LongIdTable("disputes") {
    val consumptionId = long("consumption_id").nullable().index()
    val cardId = long("card_id").index()
    val cardNo = varchar("card_no", 20)
    val studentId = varchar("student_id", 20)
    val disputeType = enumerationByName("dispute_type", 40, DisputeType::class)
    val disputedAmount = decimal("disputed_amount", 12, 2)
    val description = varchar("description", 1000)
    val evidence = varchar("evidence", 2000).nullable()
    val status = enumerationByName("status", 30, DisputeStatus::class)
    val terminalId = varchar("terminal_id", 50).nullable()
    val submittedAt = datetime("submitted_at")
    val reviewedAt = datetime("reviewed_at").nullable()
    val reviewerId = varchar("reviewer_id", 50).nullable()
    val reviewNote = varchar("review_note", 1000).nullable()
    val refundAmount = decimal("refund_amount", 12, 2).nullable()
    val refundedAt = datetime("refunded_at").nullable()
    val closedAt = datetime("closed_at").nullable()
    val rejectReason = varchar("reject_reason", 1000).nullable()
}

object CardOperations : LongIdTable("card_operations") {
    val cardId = long("card_id").index()
    val cardNo = varchar("card_no", 20)
    val operationType = varchar("operation_type", 50)
    val beforeStatus = enumerationByName("before_status", 20, CardStatus::class).nullable()
    val afterStatus = enumerationByName("after_status", 20, CardStatus::class).nullable()
    val operatorId = varchar("operator_id", 50).nullable()
    val operatedAt = datetime("operated_at")
    val detail = varchar("detail", 1000).nullable()
}
