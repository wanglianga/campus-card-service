package com.campus.card.service

import com.campus.card.dto.*
import com.campus.card.exception.*
import com.campus.card.model.*
import com.typesafe.config.ConfigFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

class ReissueService(
    private val cardService: CardService
) {
    private val config = ConfigFactory.load()
    private val reissueFee: BigDecimal = config.getDouble("service.reissueFee").toBigDecimal()
    private val defaultPickupLocation: String = config.getString("service.defaultPickupLocation")

    fun requestReissue(request: RequestReissueRequest): ReissueResponse = transaction {
        val oldCardRow = Cards.select { Cards.cardNo eq request.oldCardNo }.firstOrNull()
            ?: throw CardNotFoundException(request.oldCardNo)

        if (oldCardRow[Cards.studentId] != request.studentId) {
            throw StudentMismatchException(request.oldCardNo, request.studentId)
        }

        val oldStatus = oldCardRow[Cards.status]
        if (oldStatus == CardStatus.CANCELLED || oldStatus == CardStatus.REISSUED) {
            throw CardStateException(
                "CANNOT_REISSUE",
                "当前卡片状态 $oldStatus 无法补卡",
                "卡片已作废或已补卡"
            )
        }

        val existingReissue = Reissues.select {
            (Reissues.oldCardNo eq request.oldCardNo) and
                (Reissues.status notInList listOf(ReissueStatus.PICKED_UP, ReissueStatus.FAILED))
        }.firstOrNull()
        if (existingReissue != null) {
            throw CardStateException(
                "REISSUE_IN_PROGRESS",
                "该卡片已有进行中的补卡申请",
                "补卡单号: ${existingReissue[Reissues.id]}"
            )
        }

        if (request.lostReportId != null) {
            val report = LostReports.select { LostReports.id eq request.lostReportId }.firstOrNull()
                ?: throw LostReportNotFoundException(request.lostReportId)
            if (report[LostReports.cardNo] != request.oldCardNo) {
                throw InvalidStateException(
                    "LOST_REPORT_MISMATCH",
                    "挂失记录与卡号不匹配"
                )
            }
        }

        val now = LocalDateTime.now()
        val pickupLoc = request.pickupLocation ?: defaultPickupLocation

        val reissueId = Reissues.insertAndGetId {
            it[lostReportId] = request.lostReportId
            it[oldCardId] = oldCardRow[Cards.id].value
            it[oldCardNo] = request.oldCardNo
            it[newCardId] = null
            it[newCardNo] = null
            it[studentId] = request.studentId
            it[studentName] = oldCardRow[Cards.studentName]
            it[status] = ReissueStatus.REQUESTED
            it[reissueFee] = this@ReissueService.reissueFee
            it[feePaid] = false
            it[pickupLocation] = pickupLoc
            it[requestedAt] = now
        }.value

        if (oldStatus != CardStatus.LOST && oldStatus != CardStatus.FROZEN) {
            Cards.update({ Cards.id eq oldCardRow[Cards.id] }) {
                it[status] = CardStatus.LOST
                it[frozenAmount] = oldCardRow[Cards.balance]
            }
            cardService.recordOperation(
                oldCardRow[Cards.id].value, request.oldCardNo,
                "REISSUE_REQUEST_OLD_CARD",
                oldStatus, CardStatus.LOST,
                request.operatorId,
                "补卡申请触发旧卡挂失，余额已冻结"
            )
        }

        logger.info { "Reissue requested: oldCard=${request.oldCardNo}, reissueId=$reissueId" }
        getReissueById(reissueId)!!
    }

    fun verifyIdentity(reissueId: Long, request: VerifyIdentityRequest): ReissueResponse = transaction {
        val row = Reissues.select { Reissues.id eq reissueId }.firstOrNull()
            ?: throw ReissueNotFoundException(reissueId)

        if (row[Reissues.status] != ReissueStatus.REQUESTED) {
            throw InvalidStateException(
                "CANNOT_VERIFY",
                "当前补卡状态 ${row[Reissues.status]} 不可核验身份",
                "只有 REQUESTED 状态可核验"
            )
        }

        if (!request.verified) {
            Reissues.update({ Reissues.id eq reissueId }) {
                it[status] = ReissueStatus.FAILED
                it[failureReason] = "身份核验未通过: ${request.note}"
                it[failedAt] = LocalDateTime.now()
            }
            return@transaction getReissueById(reissueId)!!
        }

        Reissues.update({ Reissues.id eq reissueId }) {
            it[status] = ReissueStatus.IDENTITY_VERIFIED
            it[identityVerifiedAt] = LocalDateTime.now()
            it[verifierId] = request.verifierId
        }

        logger.info { "Identity verified: reissueId=$reissueId, verifier=${request.verifierId}" }
        getReissueById(reissueId)!!
    }

    fun payFee(reissueId: Long, request: PayFeeRequest): ReissueResponse = transaction {
        val row = Reissues.select { Reissues.id eq reissueId }.firstOrNull()
            ?: throw ReissueNotFoundException(reissueId)

        if (row[Reissues.status] != ReissueStatus.IDENTITY_VERIFIED) {
            throw InvalidStateException(
                "CANNOT_PAY_FEE",
                "当前补卡状态 ${row[Reissues.status]} 不可缴费",
                "只有 IDENTITY_VERIFIED 状态可缴费"
            )
        }

        if (request.paidAmount != row[Reissues.reissueFee]) {
            throw InvalidStateException(
                "FEE_AMOUNT_MISMATCH",
                "缴费金额不匹配",
                "应收 ${row[Reissues.reissueFee]}, 实收 ${request.paidAmount}"
            )
        }

        Reissues.update({ Reissues.id eq reissueId }) {
            it[status] = ReissueStatus.PAYMENT_COMPLETED
            it[feePaid] = true
            it[feePaidAt] = LocalDateTime.now()
        }

        logger.info { "Fee paid: reissueId=$reissueId, amount=${request.paidAmount}" }
        getReissueById(reissueId)!!
    }

    fun assignBatch(reissueId: Long, request: AssignBatchRequest): ReissueResponse = transaction {
        val row = Reissues.select { Reissues.id eq reissueId }.firstOrNull()
            ?: throw ReissueNotFoundException(reissueId)

        if (row[Reissues.status] != ReissueStatus.PAYMENT_COMPLETED) {
            throw InvalidStateException(
                "CANNOT_ASSIGN_BATCH",
                "当前补卡状态 ${row[Reissues.status]} 不可分配批次",
                "只有 PAYMENT_COMPLETED 状态可分配"
            )
        }

        val batch = CardBatches.select { CardBatches.id eq request.batchId }.firstOrNull()
            ?: throw BatchNotFoundException(request.batchId)

        Reissues.update({ Reissues.id eq reissueId }) {
            it[status] = ReissueStatus.CARD_MAKING
            it[batchId] = request.batchId
        }

        CardBatches.update({ CardBatches.id eq request.batchId }) {
            it[totalCards] = batch[CardBatches.totalCards] + 1
        }

        logger.info { "Batch assigned: reissueId=$reissueId, batchId=${request.batchId}" }
        getReissueById(reissueId)!!
    }

    fun markCardReady(reissueId: Long, request: MarkCardReadyRequest): ReissueResponse = transaction {
        val row = Reissues.select { Reissues.id eq reissueId }.firstOrNull()
            ?: throw ReissueNotFoundException(reissueId)

        if (row[Reissues.status] != ReissueStatus.CARD_MAKING) {
            throw InvalidStateException(
                "CANNOT_MARK_READY",
                "当前补卡状态 ${row[Reissues.status]} 不可标记制卡完成",
                "只有 CARD_MAKING 状态可标记"
            )
        }

        if (Cards.select { Cards.cardNo eq request.newCardNo }.count() > 0L) {
            throw DuplicateCardException(request.newCardNo)
        }

        val now = LocalDateTime.now()
        val oldCardRow = Cards.select { Cards.id eq row[Reissues.oldCardId] }.firstOrNull()
            ?: throw CardNotFoundException(row[Reissues.oldCardNo])

        val newCardId = Cards.insertAndGetId {
            it[cardNo] = request.newCardNo
            it[studentId] = row[Reissues.studentId]
            it[studentName] = row[Reissues.studentName]
            it[status] = CardStatus.REISSUED
            it[balance] = oldCardRow[Cards.balance]
            it[frozenAmount] = oldCardRow[Cards.frozenAmount]
            it[createdAt] = now
            it[lastActiveAt] = now
            it[previousCardId] = oldCardRow[Cards.id].value
        }.value

        cardService.recordOperation(
            newCardId, request.newCardNo,
            "NEW_CARD_CREATED",
            null, CardStatus.REISSUED,
            null,
            "新卡创建完成，关联旧卡 ${row[Reissues.oldCardNo]}，待领取激活"
        )

        Reissues.update({ Reissues.id eq reissueId }) {
            it[status] = ReissueStatus.CARD_READY
            it[newCardId] = newCardId
            it[newCardNo] = request.newCardNo
            it[cardReadyAt] = now
        }

        if (row[Reissues.batchId] != null) {
            val batch = CardBatches.select { CardBatches.id eq row[Reissues.batchId]!! }.firstOrNull()
            if (batch != null) {
                CardBatches.update({ CardBatches.id eq row[Reissues.batchId]!! }) {
                    it[finishedCards] = batch[CardBatches.finishedCards] + 1
                }
            }
        }

        logger.info { "Card ready: reissueId=$reissueId, newCard=${request.newCardNo}" }
        getReissueById(reissueId)!!
    }

    fun pickupCard(reissueId: Long, request: PickupCardRequest): ReissueResponse = transaction {
        val row = Reissues.select { Reissues.id eq reissueId }.firstOrNull()
            ?: throw ReissueNotFoundException(reissueId)

        if (row[Reissues.status] != ReissueStatus.CARD_READY) {
            throw InvalidStateException(
                "CANNOT_PICKUP",
                "当前补卡状态 ${row[Reissues.status]} 不可领取",
                "只有 CARD_READY 状态可领取"
            )
        }

        val now = LocalDateTime.now()
        val newCardId = row[Reissues.newCardId] ?: throw CardStateException("NEW_CARD_MISSING", "新卡信息缺失")

        val oldCardRow = Cards.select { Cards.id eq row[Reissues.oldCardId] }.firstOrNull()
            ?: throw CardNotFoundException(row[Reissues.oldCardNo])

        val oldBalance = oldCardRow[Cards.balance]

        Cards.update({ Cards.id eq row[Reissues.oldCardId] }) {
            it[status] = CardStatus.CANCELLED
            it[cancelledAt] = now
            it[balance] = BigDecimal.ZERO
        }

        cardService.recordOperation(
            row[Reissues.oldCardId],
            row[Reissues.oldCardNo],
            "OLD_CARD_CANCELLED",
            oldCardRow[Cards.status],
            CardStatus.CANCELLED,
            request.receiverId,
            "补卡领取后旧卡作废，余额 $oldBalance 已迁移至新卡"
        )

        Cards.update({ Cards.id eq newCardId }) {
            it[status] = CardStatus.ACTIVE
            it[frozenAmount] = BigDecimal.ZERO
            it[lastActiveAt] = now
        }

        cardService.recordOperation(
            newCardId,
            row[Reissues.newCardNo]!!,
            "NEW_CARD_ACTIVATED",
            CardStatus.REISSUED,
            CardStatus.ACTIVE,
            request.receiverId,
            "领取激活成功，余额 $oldBalance 已迁移"
        )

        Reissues.update({ Reissues.id eq reissueId }) {
            it[status] = ReissueStatus.PICKED_UP
            it[pickedUpAt] = now
            it[receiverId] = request.receiverId
            it[balanceTransferred] = true
            it[balanceTransferredAt] = now
            it[oldCardCancelled] = true
            it[oldCardCancelledAt] = now
        }

        if (row[Reissues.lostReportId] != null) {
            LostReports.update({ LostReports.id eq row[Reissues.lostReportId]!! }) {
                it[status] = LostReportStatus.REISSUED
            }
        }

        logger.info { "Card picked up: reissueId=$reissueId, newCard=${row[Reissues.newCardNo]}, balance=$oldBalance transferred" }
        getReissueById(reissueId)!!
    }

    fun failReissue(reissueId: Long, request: FailReissueRequest): ReissueResponse = transaction {
        val row = Reissues.select { Reissues.id eq reissueId }.firstOrNull()
            ?: throw ReissueNotFoundException(reissueId)

        if (row[Reissues.status] == ReissueStatus.PICKED_UP || row[Reissues.status] == ReissueStatus.FAILED) {
            throw InvalidStateException(
                "CANNOT_FAIL",
                "当前补卡状态 ${row[Reissues.status]} 不可标记失败",
                "已领取或已失败的补卡不可变更"
            )
        }

        Reissues.update({ Reissues.id eq reissueId }) {
            it[status] = ReissueStatus.FAILED
            it[failureReason] = request.failureReason
            it[failedAt] = LocalDateTime.now()
        }

        logger.info { "Reissue failed: reissueId=$reissueId, reason=${request.failureReason}" }
        getReissueById(reissueId)!!
    }

    fun getReissue(reissueId: Long): ReissueResponse = transaction {
        getReissueById(reissueId) ?: throw ReissueNotFoundException(reissueId)
    }

    fun listReissues(status: ReissueStatus? = null, studentId: String? = null, batchId: Long? = null): List<ReissueResponse> = transaction {
        var query = Reissues.selectAll()
        if (status != null) query = query.andWhere { Reissues.status eq status }
        if (studentId != null) query = query.andWhere { Reissues.studentId eq studentId }
        if (batchId != null) query = query.andWhere { Reissues.batchId eq batchId }
        query.orderBy(Reissues.requestedAt, SortOrder.DESC).map { toReissueResponse(it) }
    }

    fun createBatch(request: CreateBatchRequest): CardBatchResponse = transaction {
        if (CardBatches.select { CardBatches.batchNo eq request.batchNo }.count() > 0L) {
            throw InvalidStateException("DUPLICATE_BATCH", "批次号已存在: ${request.batchNo}")
        }

        val id = CardBatches.insertAndGetId {
            it[batchNo] = request.batchNo
            it[createdAt] = LocalDateTime.now()
            it[totalCards] = 0
            it[finishedCards] = 0
            it[operatorId] = request.operatorId
            it[remark] = request.remark
        }.value

        getBatchById(id)!!
    }

    fun getBatch(batchId: Long): CardBatchResponse = transaction {
        getBatchById(batchId) ?: throw BatchNotFoundException(batchId)
    }

    fun listBatches(): List<CardBatchResponse> = transaction {
        CardBatches.selectAll().orderBy(CardBatches.createdAt, SortOrder.DESC).map { toBatchResponse(it) }
    }

    private fun getReissueById(id: Long): ReissueResponse? = transaction {
        Reissues.select { Reissues.id eq id }.firstOrNull()?.let { toReissueResponse(it) }
    }

    private fun getBatchById(id: Long): CardBatchResponse? = transaction {
        CardBatches.select { CardBatches.id eq id }.firstOrNull()?.let { toBatchResponse(it) }
    }

    internal fun toReissueResponse(row: ResultRow): ReissueResponse = ReissueResponse(
        id = row[Reissues.id].value,
        lostReportId = row[Reissues.lostReportId],
        oldCardId = row[Reissues.oldCardId],
        oldCardNo = row[Reissues.oldCardNo],
        newCardId = row[Reissues.newCardId],
        newCardNo = row[Reissues.newCardNo],
        studentId = row[Reissues.studentId],
        studentName = row[Reissues.studentName],
        status = row[Reissues.status],
        reissueFee = row[Reissues.reissueFee],
        feePaid = row[Reissues.feePaid],
        feePaidAt = row[Reissues.feePaidAt],
        identityVerifiedAt = row[Reissues.identityVerifiedAt],
        verifierId = row[Reissues.verifierId],
        batchId = row[Reissues.batchId],
        pickupLocation = row[Reissues.pickupLocation],
        requestedAt = row[Reissues.requestedAt],
        cardReadyAt = row[Reissues.cardReadyAt],
        pickedUpAt = row[Reissues.pickedUpAt],
        receiverId = row[Reissues.receiverId],
        balanceTransferred = row[Reissues.balanceTransferred],
        balanceTransferredAt = row[Reissues.balanceTransferredAt],
        oldCardCancelled = row[Reissues.oldCardCancelled],
        oldCardCancelledAt = row[Reissues.oldCardCancelledAt],
        failureReason = row[Reissues.failureReason],
        failedAt = row[Reissues.failedAt]
    )

    internal fun toBatchResponse(row: ResultRow): CardBatchResponse = CardBatchResponse(
        id = row[CardBatches.id].value,
        batchNo = row[CardBatches.batchNo],
        createdAt = row[CardBatches.createdAt],
        totalCards = row[CardBatches.totalCards],
        finishedCards = row[CardBatches.finishedCards],
        operatorId = row[CardBatches.operatorId],
        remark = row[CardBatches.remark]
    )
}
