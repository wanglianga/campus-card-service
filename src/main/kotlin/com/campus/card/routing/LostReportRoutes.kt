package com.campus.card.routing

import com.campus.card.dto.*
import com.campus.card.model.LostReportStatus
import com.campus.card.service.LostReportService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.lostReportRoutes(lostReportService: LostReportService) {
    route("/api/lost-reports") {

        post {
            val request = call.receive<SubmitLostReportRequest>()
            val response = lostReportService.submitLostReport(request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            call.respond(lostReportService.getReport(id))
        }

        get {
            val status = call.parameters["status"]?.let { LostReportStatus.valueOf(it) }
            val studentId = call.parameters["studentId"]
            val cardNo = call.parameters["cardNo"]
            call.respond(lostReportService.listReports(status, studentId, cardNo))
        }

        post("/{id}/cancel") {
            val id = call.parameters["id"]!!.toLong()
            val request = call.receive<CancelLostReportRequest>()
            call.respond(lostReportService.cancelLostReport(id, request))
        }
    }
}
