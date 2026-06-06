package com.campus.card

import com.campus.card.config.DatabaseFactory
import com.campus.card.dto.ApiError
import com.campus.card.exception.ServiceException
import com.campus.card.routing.*
import com.campus.card.service.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

private val logger = KotlinLogging.logger {}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    val cardService = CardService()
    val consumptionService = ConsumptionService(cardService)
    val lostReportService = LostReportService(cardService)
    val reissueService = ReissueService(cardService)
    val disputeService = DisputeService(cardService, consumptionService)

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(StatusPages) {
        exception<ServiceException> { call, ex ->
            logger.warn { "Service exception: ${ex.code} - ${ex.message}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = ex.code, message = ex.message, details = ex.details)
            )
        }
        exception<IllegalArgumentException> { call, ex ->
            logger.warn { "Illegal argument: ${ex.message}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "INVALID_ARGUMENT", message = ex.message ?: "无效参数")
            )
        }
        exception<Exception> { call, ex ->
            logger.error(ex) { "Unhandled exception" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "INTERNAL_ERROR", message = "服务器内部错误", details = ex.message)
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiError(code = "NOT_FOUND", message = "资源不存在", details = call.request.uri)
            )
        }
    }

    routing {
        cardRoutes(cardService, consumptionService)
        lostReportRoutes(lostReportService)
        reissueRoutes(reissueService)
        consumptionRoutes(consumptionService)
        disputeRoutes(disputeService)
    }

    logger.info { "Campus Card Service started successfully on port 8080" }
}
