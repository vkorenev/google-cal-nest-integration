package services

import java.time._

import models.google.calendar.{AllDayEvent, GoogleApi, TimedEvent}
import org.junit.runner.RunWith
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class CalendarHelperSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  val now = Instant.parse("2000-01-01T00:00:00Z")
  val clock = Clock.fixed(now, ZoneOffset.UTC)
  val googleAccessToken = "google_token"
  val calendarId = "calendar_id"

  val upcomingEventsWindowBeginsIn = Duration.ofMinutes(10)
  val upcomingEventsWindowEndsIn = Duration.ofMinutes(30)
  val appConfig = mock[AppConfig]
  appConfig.upcomingEventsWindowBeginsIn returns upcomingEventsWindowBeginsIn
  appConfig.upcomingEventsWindowEndsIn returns upcomingEventsWindowEndsIn

  "CalendarHelper" should {
    "get events at home from Google Calendar" in {
      val googleApi = mock[GoogleApi]
      val calendarHelper = new CalendarHelper(clock, appConfig, googleApi)

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

      val eventsFuture = calendarHelper.getUpcomingEventsAtHome(googleAccessToken, Seq(calendarId))
      eventsFuture must contain(exactly(upcomingEventAtHome)).await
    }
  }

  "isAtHome" should {
    import CalendarHelper._

    val time = OffsetDateTime.parse("2016-01-01T00:00:00+03:00")

    "return true for events at home" in {
      isAtHome(TimedEvent("id", "summary", None, Some("home"), time, time)) must beTrue
      isAtHome(TimedEvent("id", "summary", None, Some("Home"), time, time)) must beTrue
      isAtHome(TimedEvent("id", "summary", None, Some("HOME"), time, time)) must beTrue
    }

    "return false for events for events not at home" in {
      isAtHome(TimedEvent("id", "summary", None, Some("work"), time, time)) must beFalse
      isAtHome(TimedEvent("id", "summary", None, Some("Mike's home"), time, time)) must beFalse
    }

    "return false for events with no location specified" in {
      isAtHome(TimedEvent("id", "summary", None, None, time, time)) must beFalse
    }
  }
}
