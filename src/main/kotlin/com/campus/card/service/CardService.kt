package com.campus.card.service

import com.campus.card.config.DatabaseFactory
import com.campus.card.dto.*
import com.campus.card.exception.*
import com.campus.card.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

class CardService {

    fun createCard(request: CreateCardRequest): CardResponse = transaction {
        if (Cards.select { Cards.cardNo eq request.cardNo }.count() > 0L) {
            throw DuplicateCardException(request.cardNo)
        }

        val now = LocalDateTime.now()
        val id = Cards.insertAndGetId {
            it[cardNo] = request.cardNo
            it[studentId] = request.studentId
            it[studentName] = request.studentName
            it[status] = CardStatus.ACTIVE
            it[balance] = request.initialBalance
            it[frozenAmount] = BigDecimal.ZERO
            it[createdAt] = now
            it[lastActiveAt] = now
        }.value

        recordOperation(id, request.cardNo, "CREATE", null, CardStatus.ACTIVE, null, "开卡成功，初始余额 ${request.initialBalance}")

        logger.info { "Card created: ${request.cardNo} for student ${request.studentId}" }
        getCardById(id)!!
    }

    fun getCard(cardNo: String): CardResponse = transaction {
        Cards.select { Cards.cardNo eq cardNo }.firstOrNull()
            ?.let { toCardResponse(it) }
            ?: throw CardNotFoundException(cardNo)
    }

    fun getCardById(id: Long): CardResponse? = transaction {
        Cards.select { Cards.id eq id }.firstOrNull()?.let { toCardResponse(it) }
    }

    fun getCardsByStudent(studentId: String): List<CardResponse> = transaction {
        Cards.select { Cards.studentId eq studentId }
            .orderBy(Cards.createdAt, SortOrder.DESC)
            .map { toCardResponse(it) }
    }

    fun getActiveCardByStudent(studentId: String): CardResponse? = transaction {
        Cards.select {
            (Cards.studentId eq studentId) and (Cards.status eq CardStatus.ACTIVE)
        }.firstOrNull()?.let { toCardResponse(it) }
    }

    fun listCards(status: CardStatus? = null, studentId: String? = null): List<CardResponse> = transaction {
        var query = Cards.selectAll()
        if (status != null) query = query.andWhere { Cards.status eq status }
        if (studentId != null) query = query.andWhere { Cards.studentId eq studentId }
        query.orderBy(Cards.createdAt, SortOrder.DESC).map { toCardResponse(it) }
    }

    fun updateCardStatus(cardId: Long, fromStatus: CardStatus, toStatus: CardStatus, operationType: String, operatorId: String?, detail: String? = null): CardResponse = transaction {
        val row = Cards.select { Cards.id eq cardId }.firstOrNull()
            ?: throw CardNotFoundException(cardId.toString())

        val current = row[Cards.status]
        if (current != fromStatus) {
            throw CardStateException(
                "INVALID_STATUS_TRANSITION",
                "无法从 $current 状态转换到 $toStatus",
                "期望状态: $fromStatus, 当前状态: $current"
            )
        }

        val now = LocalDateTime.now()
        Cards.update({ Cards.id eq cardId }) {
            it[status] = toStatus
            if (toStatus == CardStatus.CANCELLED) {
                it[cancelledAt] = now
            }
        }

        recordOperation(cardId, row[Cards.cardNo], operationType, fromStatus, toStatus, operatorId, detail)

        logger.info { "Card ${row[Cards.cardNo]} status changed: $fromStatus -> $toStatus" }
        getCardById(cardId)!!
    }

    fun getOperations(cardId: Long): List<CardOperationResponse> = transaction {
        CardOperations.select { CardOperations.cardId eq cardId }
            .orderBy(CardOperations.operatedAt, SortOrder.DESC)
            .map { toCardOperationResponse(it) }
    }

    fun getBlacklist(): List<BlacklistEntry> = transaction {
        val entries = mutableListOf<BlacklistEntry>()

        Cards.select {
            (Cards.status eq CardStatus.LOST) or (Cards.status eq CardStatus.FROZEN) or (Cards.status eq CardStatus.CANCELLED)
        }.forEach { row ->
            val reason = when (row[Cards.status]) {
                CardStatus.LOST -> "卡片已挂失"
                CardStatus.FROZEN -> "卡片已冻结"
                CardStatus.CANCELLED -> "卡片已作废"
                else -> "不可用"
            }
            entries.add(
                BlacklistEntry(
                    cardNo = row[Cards.cardNo],
                    studentId = row[Cards.studentId],
                    studentName = row[Cards.studentName],
                    reason = reason,
                    effectiveAt = row[Cards.cancelledAt] ?: row[Cards.lastActiveAt] ?: row[Cards.createdAt]
                )
            )
        }
        entries
    }

    internal fun recordOperation(
        cardId: Long,
        cardNo: String,
        operationType: String,
        beforeStatus: CardStatus?,
        afterStatus: CardStatus?,
        operatorId: String?,
        detail: String? = null
    ) {
        transaction {
            CardOperations.insertAndGetId {
                it[CardOperations.cardId] = cardId
                it[CardOperations.cardNo] = cardNo
                it[CardOperations.operationType] = operationType
                it[CardOperations.beforeStatus] = beforeStatus
                it[CardOperations.afterStatus] = afterStatus
                it[CardOperations.operatorId] = operatorId
                it[operatedAt] = LocalDateTime.now()
                it[CardOperations.detail] = detail
            }
        }
    }

    internal fun toCardResponse(row: ResultRow): CardResponse = CardResponse(
        id = row[Cards.id].value,
        cardNo = row[Cards.cardNo],
        studentId = row[Cards.studentId],
        studentName = row[Cards.studentName],
        status = row[Cards.status],
        balance = row[Cards.balance],
        frozenAmount = row[Cards.frozenAmount],
        createdAt = row[Cards.createdAt],
        lastActiveAt = row[Cards.lastActiveAt],
        cancelledAt = row[Cards.cancelledAt],
        previousCardId = row[Cards.previousCardId]
    )

    internal fun toCardOperationResponse(row: ResultRow): CardOperationResponse = CardOperationResponse(
        id = row[CardOperations.id].value,
        cardId = row[CardOperations.cardId],
        cardNo = row[CardOperations.cardNo],
        operationType = row[CardOperations.operationType],
        beforeStatus = row[CardOperations.beforeStatus],
        afterStatus = row[CardOperations.afterStatus],
        operatorId = row[CardOperations.operatorId],
        operatedAt = row[CardOperations.operatedAt],
        detail = row[CardOperations.detail]
    )
}
