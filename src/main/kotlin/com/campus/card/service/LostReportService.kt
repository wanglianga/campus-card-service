package com.campus.card.service

import com.campus.card.dto.*
import com.campus.card.exception.*
import com.campus.card.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

class LostReportService(
    private val cardService: CardService
) {

    fun submitLostReport(request: SubmitLostReportRequest): LostReportResponse = transaction {
        val cardRow = Cards.select { Cards.cardNo eq request.cardNo }.firstOrNull()
            ?: throw CardNotFoundException(request.cardNo)

        if (cardRow[Cards.studentId] != request.studentId) {
            throw StudentMismatchException(request.cardNo, request.studentId)
        }

        val currentStatus = cardRow[Cards.status]
        if (currentStatus == CardStatus.CANCELLED || currentStatus == CardStatus.REISSUED) {
            throw CardStateException(
                "CANNOT_REPORT_LOST",
                "当前卡片状态 $currentStatus 无法挂失",
                "卡片已作废或已补卡"
            )
        }
        if (currentStatus == CardStatus.LOST || currentStatus == CardStatus.FROZEN) {
            val existing = LostReports.select {
                (LostReports.cardNo eq request.cardNo) and
                    ((LostReports.status eq LostReportStatus.PENDING) or
                        (LostReports.status eq LostReportStatus.CONFIRMED))
            }.firstOrNull()
            if (existing != null) {
                throw CardStateException(
                    "LOST_ALREADY_REPORTED",
                    "该卡片已处于挂失状态",
                    "挂失单号: ${existing[LostReports.id]}"
                )
            }
        }

        val now = LocalDateTime.now()
        val reportId = LostReports.insertAndGetId {
            it[cardId] = cardRow[Cards.id].value
            it[cardNo] = request.cardNo
            it[studentId] = request.studentId
            it[studentName] = cardRow[Cards.studentName]
            it[channel] = request.channel
            it[reporterContact] = request.reporterContact
            it[status] = LostReportStatus.CONFIRMED
            it[reportedAt] = now
            it[confirmedAt] = now
            it[remark] = request.remark
        }.value

        Cards.update({ Cards.id eq cardRow[Cards.id] }) {
            it[status] = CardStatus.LOST
            it[frozenAmount] = cardRow[Cards.balance]
        }

        cardService.recordOperation(
            cardId = cardRow[Cards.id].value,
            cardNo = request.cardNo,
            operationType = "REPORT_LOST",
            beforeStatus = currentStatus,
            afterStatus = CardStatus.LOST,
            operatorId = null,
            detail = "通过 ${request.channel} 挂失成功，余额 ${cardRow[Cards.balance]} 已冻结"
        )

        logger.info { "Lost report submitted: card=${request.cardNo}, channel=${request.channel}, reportId=$reportId" }
        getReportById(reportId)!!
    }

    fun cancelLostReport(reportId: Long, request: CancelLostReportRequest): LostReportResponse = transaction {
        val reportRow = LostReports.select { LostReports.id eq reportId }.firstOrNull()
            ?: throw LostReportNotFoundException(reportId)

        val currentStatus = reportRow[LostReports.status]
        if (currentStatus != LostReportStatus.PENDING && currentStatus != LostReportStatus.CONFIRMED) {
            throw InvalidStateException(
                "CANNOT_CANCEL_LOST_REPORT",
                "当前挂失状态 $currentStatus 不可取消",
                "只有 PENDING 或 CONFIRMED 状态可取消"
            )
        }

        val now = LocalDateTime.now()
        val newStatus = if (request.isMisreport) LostReportStatus.CANCELLED_MISREPORT else LostReportStatus.CANCELLED_FOUND

        LostReports.update({ LostReports.id eq reportId }) {
            it[status] = newStatus
            it[cancelledAt] = now
            it[cancelReason] = request.cancelReason
            it[cancelledByMisreport] = request.isMisreport
            it[operatorId] = request.operatorId
        }

        val cardRow = Cards.select { Cards.id eq reportRow[LostReports.cardId] }.firstOrNull()
        if (cardRow != null && (cardRow[Cards.status] == CardStatus.LOST || cardRow[Cards.status] == CardStatus.FROZEN)) {
            Cards.update({ Cards.id eq reportRow[LostReports.cardId] }) {
                it[status] = CardStatus.ACTIVE
                it[frozenAmount] = java.math.BigDecimal.ZERO
            }

            val reason = if (request.isMisreport) "误挂失取消" else "找回旧卡解挂"
            cardService.recordOperation(
                cardId = reportRow[LostReports.cardId],
                cardNo = reportRow[LostReports.cardNo],
                operationType = if (request.isMisreport) "CANCEL_MISREPORT" else "UNFREEZE_FOUND",
                beforeStatus = cardRow[Cards.status],
                afterStatus = CardStatus.ACTIVE,
                operatorId = request.operatorId,
                detail = "$reason，余额已解冻。原因：${request.cancelReason}"
            )
        }

        logger.info { "Lost report cancelled: reportId=$reportId, isMisreport=${request.isMisreport}" }
        getReportById(reportId)!!
    }

    fun getReport(reportId: Long): LostReportResponse = transaction {
        getReportById(reportId) ?: throw LostReportNotFoundException(reportId)
    }

    fun listReports(status: LostReportStatus? = null, studentId: String? = null, cardNo: String? = null): List<LostReportResponse> = transaction {
        var query = LostReports.selectAll()
        if (status != null) query = query.andWhere { LostReports.status eq status }
        if (studentId != null) query = query.andWhere { LostReports.studentId eq studentId }
        if (cardNo != null) query = query.andWhere { LostReports.cardNo eq cardNo }
        query.orderBy(LostReports.reportedAt, SortOrder.DESC).map { toReportResponse(it) }
    }

    private fun getReportById(id: Long): LostReportResponse? = transaction {
        LostReports.select { LostReports.id eq id }.firstOrNull()?.let { toReportResponse(it) }
    }

    internal fun toReportResponse(row: ResultRow): LostReportResponse = LostReportResponse(
        id = row[LostReports.id].value,
        cardId = row[LostReports.cardId],
        cardNo = row[LostReports.cardNo],
        studentId = row[LostReports.studentId],
        studentName = row[LostReports.studentName],
        channel = row[LostReports.channel],
        reporterContact = row[LostReports.reporterContact],
        status = row[LostReports.status],
        reportedAt = row[LostReports.reportedAt],
        confirmedAt = row[LostReports.confirmedAt],
        cancelledAt = row[LostReports.cancelledAt],
        cancelReason = row[LostReports.cancelReason],
        cancelledByMisreport = row[LostReports.cancelledByMisreport],
        operatorId = row[LostReports.operatorId],
        remark = row[LostReports.remark]
    )
}
