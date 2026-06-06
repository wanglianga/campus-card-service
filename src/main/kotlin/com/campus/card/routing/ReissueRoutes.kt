package com.campus.card.routing

import com.campus.card.dto.*
import com.campus.card.model.ReissueStatus
import com.campus.card.service.ReissueService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.reissueRoutes(reissueService: ReissueService) {
    route("/api/reissues") {

        post {
            val request = call.receive<RequestReissueRequest>()
            val response = reissueService.requestReissue(request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            call.respond(reissueService.getReissue(id))
        }

        get {
            val status = call.parameters["status"]?.let { ReissueStatus.valueOf(it) }
            val studentId = call.parameters["studentId"]
            val batchId = call.parameters["batchId"]?.toLongOrNull()
            call.respond(reissueService.listReissues(status, studentId, batchId))
        }

        post("/{id}/verify-identity") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<VerifyIdentityRequest>()
            call.respond(reissueService.verifyIdentity(id, request))
        }

        post("/{id}/pay-fee") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<PayFeeRequest>()
            call.respond(reissueService.payFee(id, request))
        }

        post("/{id}/assign-batch") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<AssignBatchRequest>()
            call.respond(reissueService.assignBatch(id, request))
        }

        post("/{id}/mark-ready") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<MarkCardReadyRequest>()
            call.respond(reissueService.markCardReady(id, request))
        }

        post("/{id}/pickup") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<PickupCardRequest>()
            call.respond(reissueService.pickupCard(id, request))
        }

        post("/{id}/fail") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<FailReissueRequest>()
            call.respond(reissueService.failReissue(id, request))
        }
    }

    route("/api/batches") {

        post {
            val request = call.receive<CreateBatchRequest>()
            val response = reissueService.createBatch(request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            call.respond(reissueService.getBatch(id))
        }

        get {
            call.respond(reissueService.listBatches())
        }
    }
}
