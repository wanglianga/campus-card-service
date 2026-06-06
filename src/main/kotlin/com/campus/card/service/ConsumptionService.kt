package com.campus.card.service

import com.campus.card.dto.*
import com.campus.card.exception.*
import com.campus.card.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

class ConsumptionService(
    private val cardService: CardService
) {

    fun recordConsumption(request: RecordConsumptionRequest): ConsumptionResponse = transaction {
        val cardRow = Cards.select { Cards.cardNo eq request.cardNo }.firstOrNull()
            ?: throw CardNotFoundException(request.cardNo)

        val cardStatus = cardRow[Cards.status]
        val isBlocked = cardStatus == CardStatus.LOST || cardStatus == CardStatus.FROZEN ||
            cardStatus == CardStatus.CANCELLED || cardStatus == CardStatus.REISSUED

        val consumedAt = request.consumedAt?.let {
            LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } ?: LocalDateTime.now()

        val finalStatus = when {
            request.isUnsynced && isBlocked -> ConsumptionStatus.PENDING_BLACKLIST
            request.isUnsynced -> ConsumptionStatus.NORMAL
            isBlocked -> ConsumptionStatus.BLOCKED
            else -> ConsumptionStatus.NORMAL
        }

        var currentBalance = cardRow[Cards.balance]

        if (!isBlocked && !request.isUnsynced) {
            if (cardRow[Cards.balance] < request.amount) {
                throw InsufficientBalanceException(request.cardNo, cardRow[Cards.balance], request.amount)
            }
            currentBalance = cardRow[Cards.balance].subtract(request.amount)
            Cards.update({ Cards.id eq cardRow[Cards.id] }) {
                it[balance] = currentBalance
                it[lastActiveAt] = LocalDateTime.now()
            }
        }

        val balanceAfter = if (isBlocked || (request.isUnsynced && isBlocked)) {
            cardRow[Cards.balance]
        } else {
            currentBalance
        }

        val id = Consumptions.insertAndGetId {
            it[cardId] = cardRow[Cards.id].value
            it[cardNo] = request.cardNo
            it[studentId] = cardRow[Cards.studentId]
            it[terminalId] = request.terminalId
            it[terminalName] = request.terminalName
            it[location] = request.location
            it[amount] = request.amount
            it[balanceAfter] = balanceAfter
            it[consumedAt] = consumedAt
            it[syncedAt] = if (request.isUnsynced) null else LocalDateTime.now()
            it[isUnsynced] = request.isUnsynced
            it[status] = finalStatus
            it[remark] = if (isBlocked) "黑名单终端消费，卡状态异常: $cardStatus" else null
        }.value

        logger.info { "Consumption recorded: card=${request.cardNo}, amount=${request.amount}, unsynced=${request.isUnsynced}, terminal=${request.terminalId}" }
        getConsumptionById(id)!!
    }

    fun syncConsumptions(request: SyncConsumptionRequest): List<ConsumptionResponse> = transaction {
        val now = LocalDateTime.now()
        request.consumptionIds.mapNotNull { id ->
            val row = Consumptions.select { Consumptions.id eq id }.firstOrNull()
            if (row != null && row[Consumptions.isUnsynced]) {
                Consumptions.update({ Consumptions.id eq id }) {
                    it[isUnsynced] = false
                    it[syncedAt] = now
                    if (row[Consumptions.status] == ConsumptionStatus.PENDING_BLACKLIST) {
                        it[status] = ConsumptionStatus.BLOCKED
                    }
                }
                getConsumptionById(id)
            } else null
        }
    }

    fun getConsumption(consumptionId: Long): ConsumptionResponse = transaction {
        getConsumptionById(consumptionId) ?: throw ConsumptionNotFoundException(consumptionId)
    }

    fun listConsumptions(
        cardNo: String? = null,
        studentId: String? = null,
        isUnsynced: Boolean? = null,
        status: ConsumptionStatus? = null,
        terminalId: String? = null
    ): List<ConsumptionResponse> = transaction {
        var query = Consumptions.selectAll()
        if (cardNo != null) query = query.andWhere { Consumptions.cardNo eq cardNo }
        if (studentId != null) query = query.andWhere { Consumptions.studentId eq studentId }
        if (isUnsynced != null) query = query.andWhere { Consumptions.isUnsynced eq isUnsynced }
        if (status != null) query = query.andWhere { Consumptions.status eq status }
        if (terminalId != null) query = query.andWhere { Consumptions.terminalId eq terminalId }
        query.orderBy(Consumptions.consumedAt, SortOrder.DESC).map { toConsumptionResponse(it) }
    }

    fun getRecentConsumptions(cardNo: String, limit: Int = 10): List<ConsumptionResponse> = transaction {
        Consumptions.select { Consumptions.cardNo eq cardNo }
            .orderBy(Consumptions.consumedAt, SortOrder.DESC)
            .limit(limit)
            .map { toConsumptionResponse(it) }
    }

    internal fun getConsumptionById(id: Long): ConsumptionResponse? = transaction {
        Consumptions.select { Consumptions.id eq id }.firstOrNull()?.let { toConsumptionResponse(it) }
    }

    internal fun toConsumptionResponse(row: ResultRow): ConsumptionResponse = ConsumptionResponse(
        id = row[Consumptions.id].value,
        cardId = row[Consumptions.cardId],
        cardNo = row[Consumptions.cardNo],
        studentId = row[Consumptions.studentId],
        terminalId = row[Consumptions.terminalId],
        terminalName = row[Consumptions.terminalName],
        location = row[Consumptions.location],
        amount = row[Consumptions.amount],
        balanceAfter = row[Consumptions.balanceAfter],
        consumedAt = row[Consumptions.consumedAt],
        syncedAt = row[Consumptions.syncedAt],
        isUnsynced = row[Consumptions.isUnsynced],
        status = row[Consumptions.status],
        remark = row[Consumptions.remark]
    )
}
