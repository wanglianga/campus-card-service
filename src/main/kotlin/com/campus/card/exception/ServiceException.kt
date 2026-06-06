package com.campus.card.exception

open class ServiceException(
    val code: String,
    override val message: String,
    val details: String? = null
) : RuntimeException(message)

class CardNotFoundException(cardNo: String) :
    ServiceException("CARD_NOT_FOUND", "卡片不存在: $cardNo")

class CardStateException(code: String, message: String, details: String? = null) :
    ServiceException(code, message, details)

class StudentMismatchException(cardNo: String, studentId: String) :
    ServiceException("STUDENT_MISMATCH", "卡号 $cardNo 与学号 $studentId 不匹配")

class LostReportNotFoundException(id: Long) :
    ServiceException("LOST_REPORT_NOT_FOUND", "挂失记录不存在: $id")

class ReissueNotFoundException(id: Long) :
    ServiceException("REISSUE_NOT_FOUND", "补卡记录不存在: $id")

class BatchNotFoundException(id: Long) :
    ServiceException("BATCH_NOT_FOUND", "制卡批次不存在: $id")

class DisputeNotFoundException(id: Long) :
    ServiceException("DISPUTE_NOT_FOUND", "争议记录不存在: $id")

class ConsumptionNotFoundException(id: Long) :
    ServiceException("CONSUMPTION_NOT_FOUND", "消费记录不存在: $id")

class DuplicateCardException(cardNo: String) :
    ServiceException("DUPLICATE_CARD", "卡号已存在: $cardNo")

class InsufficientBalanceException(cardNo: String, balance: java.math.BigDecimal, amount: java.math.BigDecimal) :
    ServiceException("INSUFFICIENT_BALANCE", "余额不足", "卡号 $cardNo 余额 $balance, 消费金额 $amount")

class InvalidStateException(code: String, message: String, details: String? = null) :
    ServiceException(code, message, details)
