package com.campus.card.routing

import com.campus.card.dto.*
import com.campus.card.model.CardStatus
import com.campus.card.service.CardService
import com.campus.card.service.ConsumptionService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.cardRoutes(cardService: CardService, consumptionService: ConsumptionService) {
    route("/api/cards") {

        post {
            val request = call.receive<CreateCardRequest>()
            val response = cardService.createCard(request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{cardNo}") {
            val cardNo = call.parameters["cardNo"]!!
            call.respond(cardService.getCard(cardNo))
        }

        get {
            val status = call.parameters["status"]?.let { CardStatus.valueOf(it) }
            val studentId = call.parameters["studentId"]
            call.respond(cardService.listCards(status, studentId))
        }

        get("/student/{studentId}") {
            val studentId = call.parameters["studentId"]!!
            call.respond(cardService.getCardsByStudent(studentId))
        }

        get("/{cardNo}/operations") {
            val cardNo = call.parameters["cardNo"]!!
            val card = cardService.getCard(cardNo)
            call.respond(cardService.getOperations(card.id))
        }

        get("/{cardNo}/recent-consumptions") {
            val cardNo = call.parameters["cardNo"]!!
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
            call.respond(consumptionService.getRecentConsumptions(cardNo, limit))
        }
    }

    get("/api/blacklist") {
        call.respond(cardService.getBlacklist())
    }

    get("/api/health") {
        call.respond(mapOf("status" to "UP", "service" to "campus-card-service"))
    }
}
