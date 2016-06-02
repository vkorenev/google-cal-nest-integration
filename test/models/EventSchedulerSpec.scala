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
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import scala.language.existentials

@RunWith(classOf[JUnitRunner])
class EventSchedulerSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  val now = Instant.parse("2000-01-01T00:00:00Z")
  val clock = Clock.fixed(now, ZoneOffset.UTC)
  val nestAccessToken = "nest_token"
  val googleAccessToken = "google_token"
  val calendarId = "calendar_id"
  val structureId = "structure_id"

  val upcomingEventsWindowBeginsIn = Duration.ofMinutes(10)
  val upcomingEventsWindowEndsIn = Duration.ofMinutes(30)
  val etaWindowBeginsBeforeEventStart = Duration.ofMinutes(15)
  val etaWindowEndsBeforeEventStart = Duration.ofMinutes(5)
  val minDurationToArrive = Duration.ofMinutes(2)
  val appConfig = mock[AppConfig]
  appConfig.upcomingEventsWindowBeginsIn returns upcomingEventsWindowBeginsIn
  appConfig.upcomingEventsWindowEndsIn returns upcomingEventsWindowEndsIn
  appConfig.etaWindowBeginsBeforeEventStart returns etaWindowBeginsBeforeEventStart
  appConfig.etaWindowEndsBeforeEventStart returns etaWindowEndsBeforeEventStart
  appConfig.minDurationToArrive returns minDurationToArrive

  "EventScheduler" should {
    "get events at home from Google Calendar" in {
      val googleApi = mock[GoogleApi]
      val eventScheduler = new EventScheduler(mock[ActorSystem], clock, mock[ApplicationLifecycle], appConfig,
        mock[NestApi], googleApi)

      def calcTimeFromNow(duration: Duration) = now.plus(duration).atOffset(ZoneOffset.UTC)

      val upcomingEventsWindowMed = Duration.ofMinutes(20)
      val upcomingEventAtHome = TimedEvent("upcomingEventAtHome", "", None, Some("home"),
        calcTimeFromNow(upcomingEventsWindowMed),
        calcTimeFromNow(upcomingEventsWindowEndsIn))

      val upcomingEventAtHomeStartedBefore = TimedEvent("tooLateEvent", "", None, Some("home"),
        calcTimeFromNow(upcomingEventsWindowBeginsIn.minus(Duration.ofMinutes(1))),
        calcTimeFromNow(upcomingEventsWindowMed))

      val upcomingEventNotAtHome = TimedEvent("upcomingEventNotAtHome", "", None, Some("work"),
        calcTimeFromNow(upcomingEventsWindowMed),
        calcTimeFromNow(upcomingEventsWindowEndsIn))

      val allDayEvent = AllDayEvent("allDayEvent", "", None, Some("work"),
        LocalDate.now(), LocalDate.now())

      googleApi.getEvents(===(googleAccessToken))(===(calendarId),
        any[Option[Instant]], any[Option[Instant]]) returns Future.successful(Seq(
          upcomingEventAtHomeStartedBefore, upcomingEventAtHome, upcomingEventNotAtHome, allDayEvent))

      val eventsFuture = eventScheduler.getUpcomingEventsAtHome(googleAccessToken, calendarId)
      eventsFuture must contain(exactly(upcomingEventAtHome)).await
    }

    val firebase = mock[Firebase]

    def nestApiMock() = {
      val nestApi = mock[NestApi]
      nestApi.withNest(any[String])(any[Firebase => Future[T forSome { type T }]]) responds {
        case Array(accessToken: String, block: (Firebase => Future[_]) @unchecked) =>
          accessToken must beEqualTo(nestAccessToken)
          block(firebase)
      }
      nestApi
    }

    "report ETA to nest" in {
      val eventStart = Instant.parse("2016-01-01T00:00:00Z")
      val eventEnd = eventStart.plus(Duration.ofHours(1))
      val event = TimedEvent("id", "", None, Some("home"),
        eventStart.atOffset(ZoneOffset.UTC), eventEnd.atOffset(ZoneOffset.UTC))

      val nestApi = nestApiMock()

      val updateResult = mock[Future[Firebase]]
      nestApi.updateETA(any[Firebase], any[String], any[String], any[Instant], any[Instant]) returns updateResult

      val eventScheduler = new EventScheduler(mock[ActorSystem], clock, mock[ApplicationLifecycle], appConfig,
        nestApi, mock[GoogleApi])
      eventScheduler.updateETA(nestAccessToken, structureId, event) must beTheSameAs(updateResult)
      there was one(nestApi).updateETA(firebase, structureId, event.id,
        event.start.toInstant.minus(etaWindowBeginsBeforeEventStart),
        event.start.toInstant.minus(etaWindowEndsBeforeEventStart))
    }

    "report ETA time in the future" in {
      val eventStart = now.plus(Duration.ofMinutes(2))
      val eventEnd = eventStart.plus(Duration.ofHours(1))
      val event = TimedEvent("id", "", None, Some("home"),
        eventStart.atOffset(ZoneOffset.UTC), eventEnd.atOffset(ZoneOffset.UTC))

      val nestApi = nestApiMock()

      val updateResult = mock[Future[Firebase]]
      nestApi.updateETA(any[Firebase], any[String], any[String], any[Instant], any[Instant]) returns updateResult

      val eventScheduler = new EventScheduler(mock[ActorSystem], clock, mock[ApplicationLifecycle], appConfig,
        nestApi, mock[GoogleApi])
      eventScheduler.updateETA(nestAccessToken, structureId, event) must beTheSameAs(updateResult)
      there was one(nestApi).updateETA(firebase, structureId, event.id,
        now.plus(minDurationToArrive),
        now.plus(minDurationToArrive))
    }
  }
}
