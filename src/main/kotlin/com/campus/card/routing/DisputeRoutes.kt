package com.campus.card.routing

import com.campus.card.dto.*
import com.campus.card.model.DisputeStatus
import com.campus.card.model.DisputeType
import com.campus.card.service.DisputeService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.disputeRoutes(disputeService: DisputeService) {
    route("/api/disputes") {

        post {
            val request = call.receive<SubmitDisputeRequest>()
            val response = disputeService.submitDispute(request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            call.respond(disputeService.getDispute(id))
        }

        get {
            val status = call.parameters["status"]?.let { DisputeStatus.valueOf(it) }
            val studentId = call.parameters["studentId"]
            val disputeType = call.parameters["disputeType"]?.let { DisputeType.valueOf(it) }
            val cardNo = call.parameters["cardNo"]
            call.respond(disputeService.listDisputes(status, studentId, disputeType, cardNo))
        }

        post("/{id}/review") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<ReviewDisputeRequest>()
            call.respond(disputeService.reviewDispute(id, request))
        }
    }
}
