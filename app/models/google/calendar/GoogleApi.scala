package models.google.calendar

import java.time.{Instant, LocalDate, OffsetDateTime}
import javax.inject.{Inject, Singleton}

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GoogleApi @Inject() (ws: WSClient)(implicit ec: ExecutionContext) {
  import GoogleApi._

  def getCalendars(accessToken: String): Future[Seq[Calendar]] =
    ws.url(
      "https://www.googleapis.com/calendar/v3/users/me/calendarList")
      .withHeaders(authHeader(accessToken))
      .get() map { response =>
        (response.json \ "items").as[Seq[Calendar]]
      }

  private def toParam(name: String)(time: Instant) = (name, time.toString)

  private def optionalParams(params: Option[(String, String)]*) = params collect {
    case Some(p) => p
  }

  def getEvents(accessToken: String)(calendarId: String, timeMin: Option[Instant], timeMax: Option[Instant]): Future[Seq[Event]] =
    ws.url(
      s"https://www.googleapis.com/calendar/v3/calendars/$calendarId/events")
      .withQueryString(optionalParams(
        timeMin.map(toParam("timeMin")),
        timeMax.map(toParam("timeMax"))): _*)
      .withHeaders(authHeader(accessToken))
      .get() map { response =>
        (response.json \ "items").as[Seq[Event]]
      }

  private def authHeader(accessToken: String) = ("Authorization", s"Bearer $accessToken")
}

object GoogleApi {
  implicit val calendarReads: Reads[Calendar] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "primary").readNullable[Boolean].map(_.getOrElse(false)) and
    (JsPath \ "summary").read[String] and
    (JsPath \ "summaryOverride").readNullable[String] and
    (JsPath \ "description").readNullable[String])(Calendar.apply _)

  implicit val eventReads: Reads[Event] = {
    val common = (JsPath \ "id").read[String] and
      (JsPath \ "summary").read[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "location").readNullable[String]

    val allDayEventReads: Reads[Event] = (common and
      (JsPath \ "start" \ "date").read[String].map(LocalDate.parse) and
      (JsPath \ "end" \ "date").read[String].map(LocalDate.parse))(AllDayEvent.apply _)

    val timeEventReads: Reads[Event] = (common and
      (JsPath \ "start" \ "dateTime").read[String].map(OffsetDateTime.parse) and
      (JsPath \ "end" \ "dateTime").read[String].map(OffsetDateTime.parse))(TimedEvent.apply _)

    allDayEventReads or timeEventReads
  }
}
