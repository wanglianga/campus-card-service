package com.campus.card.model

enum class CardStatus {
    ACTIVE,
    LOST,
    FROZEN,
    REISSUED,
    CANCELLED
}

enum class LostChannel {
    MOBILE_APP,
    WEB_PORTAL,
    CARD_CENTER,
    IVR_PHONE,
    WECHAT_MINIAPP
}

enum class LostReportStatus {
    PENDING,
    CONFIRMED,
    CANCELLED_MISREPORT,
    CANCELLED_FOUND,
    REISSUED
}

enum class ReissueStatus {
    REQUESTED,
    IDENTITY_VERIFIED,
    PAYMENT_COMPLETED,
    CARD_MAKING,
    CARD_READY,
    PICKED_UP,
    FAILED
}

enum class DisputeType {
    UNSYNCED_TERMINAL_CONSUMPTION,
    FRAUDULENT_CHARGE,
    SYSTEM_ERROR,
    DOUBLE_CHARGE,
    WRONG_AMOUNT
}

enum class DisputeStatus {
    SUBMITTED,
    UNDER_REVIEW,
    REFUND_APPROVED,
    REFUND_PROCESSED,
    REJECTED,
    CLOSED
}

enum class ConsumptionStatus {
    NORMAL,
    PENDING_BLACKLIST,
    BLOCKED,
    DISPUTED,
    REFUNDED
}
