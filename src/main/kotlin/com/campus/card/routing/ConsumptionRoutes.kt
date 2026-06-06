package com.campus.card.routing

import com.campus.card.dto.*
import com.campus.card.model.ConsumptionStatus
import com.campus.card.service.ConsumptionService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.consumptionRoutes(consumptionService: ConsumptionService) {
    route("/api/consumptions") {

        post {
            val request = call.receive<RecordConsumptionRequest>()
            val response = consumptionService.recordConsumption(request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            call.respond(consumptionService.getConsumption(id))
        }

        get {
            val cardNo = call.parameters["cardNo"]
            val studentId = call.parameters["studentId"]
            val isUnsynced = call.parameters["isUnsynced"]?.toBoolean()
            val status = call.parameters["status"]?.let { ConsumptionStatus.valueOf(it) }
            val terminalId = call.parameters["terminalId"]
            call.respond(consumptionService.listConsumptions(cardNo, studentId, isUnsynced, status, terminalId))
        }

        post("/sync") {
            val request = call.receive<SyncConsumptionRequest>()
            call.respond(consumptionService.syncConsumptions(request))
        }

        get("/card/{cardNo}/recent") {
            val cardNo = call.parameters["cardNo"]!!
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
            call.respond(consumptionService.getRecentConsumptions(cardNo, limit))
        }
    }
}
