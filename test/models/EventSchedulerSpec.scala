package models

import java.time._

import akka.actor.ActorSystem
import com.firebase.client.Firebase
import models.google.calendar.{AllDayEvent, GoogleApi, TimedEvent}
import models.nest.NestApi
import org.junit.runner.RunWith
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.concurrent.Future
import scala.language.existentials

@RunWith(classOf[JUnitRunner])
class EventSchedulerSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "EventScheduler" should {
    "get events at home from Google Calendar" in {
      val googleAccessToken = "google_token"
      val calendarId = "calendar_id"
      val googleApi = mock[GoogleApi]
      val eventScheduler = new EventScheduler(mock[ActorSystem], mock[NestApi], googleApi)

      val now = Instant.now()
      def calcTimeFromNow(duration: Duration) = now.plus(duration).atOffset(ZoneOffset.UTC)

      val upcomingEventsWindowMed = Duration.ofMillis(
        eventScheduler.upcomingEventsWindowBeginsIn.plus(eventScheduler.upcomingEventsWindowEndsIn).toMillis / 2)
      val upcomingEventAtHome = TimedEvent("upcomingEventAtHome", "", None, Some("home"),
        calcTimeFromNow(upcomingEventsWindowMed),
        calcTimeFromNow(eventScheduler.upcomingEventsWindowEndsIn))

      val upcomingEventAtHomeStartedBefore = TimedEvent("tooLateEvent", "", None, Some("home"),
        calcTimeFromNow(eventScheduler.upcomingEventsWindowBeginsIn.minus(Duration.ofMinutes(1))),
        calcTimeFromNow(upcomingEventsWindowMed))

      val upcomingEventNotAtHome = TimedEvent("upcomingEventNotAtHome", "", None, Some("work"),
        calcTimeFromNow(upcomingEventsWindowMed),
        calcTimeFromNow(eventScheduler.upcomingEventsWindowEndsIn))

      val allDayEvent = AllDayEvent("allDayEvent", "", None, Some("work"),
        LocalDate.now(), LocalDate.now())

      googleApi.getEvents(===(googleAccessToken))(===(calendarId),
        any[Option[Instant]], any[Option[Instant]]) returns Future.successful(Seq(
          upcomingEventAtHomeStartedBefore, upcomingEventAtHome, upcomingEventNotAtHome, allDayEvent))

      val eventsFuture = eventScheduler.getUpcomingEventsAtHome(googleAccessToken, calendarId)
      eventsFuture must contain(exactly(upcomingEventAtHome)).await
    }

    "report ETA to nest" in {
      val nestAccessToken = "nest_token"
      val structureId = "structure_id"
      val eventStart = Instant.parse("2016-01-01T00:00:00Z")
      val eventEnd = eventStart.plus(Duration.ofHours(1))
      val event = TimedEvent("id", "", None, Some("home"),
        eventStart.atOffset(ZoneOffset.UTC), eventEnd.atOffset(ZoneOffset.UTC))

      val nestApi = mock[NestApi]
      val firebase = mock[Firebase]
      nestApi.withNest(any[String])(any[Firebase => Future[T forSome { type T }]]) responds {
        case Array(accessToken: String, block: (Firebase => Future[_])) =>
          accessToken must beEqualTo(nestAccessToken)
          block(firebase)
      }

      val updateResult = mock[Future[Firebase]]
      nestApi.updateETA(any[Firebase], any[String], any[String], any[Instant], any[Instant]) returns updateResult

      val eventScheduler = new EventScheduler(mock[ActorSystem], nestApi, mock[GoogleApi])
      eventScheduler.updateETA(nestAccessToken, structureId, event) must beTheSameAs(updateResult)
      there was one(nestApi).updateETA(firebase, structureId, event.id,
        event.start.toInstant.minus(eventScheduler.etaWindowBeginsBeforeEventStart),
        event.start.toInstant.minus(eventScheduler.etaWindowEndsBeforeEventStart))
    }
  }
}
