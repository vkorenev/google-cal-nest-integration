package services

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}

import models.AppConfig
import models.google.calendar.{Event, GoogleApi, TimedEvent}
import utils.JavaConversions.instantOrdering._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CalendarHelper @Inject() (
  clock: Clock,
  appConfig: AppConfig,
  googleApi: GoogleApi)(implicit exec: ExecutionContext) {

  import CalendarHelper._
  import appConfig._

  private[services] def getUpcomingEventsAtHome(
    accessToken: String, calendarIds: Iterable[String]): Future[Iterable[TimedEvent]] = {
    val now = Instant.now(clock)
    val intervalStart = now.plus(upcomingEventsWindowBeginsIn)
    val intervalEnd = now.plus(upcomingEventsWindowEndsIn)

    Future.traverse(calendarIds) { calendarId =>
      googleApi.getEvents(accessToken)(calendarId, Some(intervalStart), Some(intervalEnd))
    } map { eventsByCalendar =>
      for {
        events <- eventsByCalendar
        event <- events.collect { case ev: TimedEvent => ev }
        if event.start.toInstant >= intervalStart && isAtHome(event)
      } yield event
    }
  }
}

object CalendarHelper {
  def isAtHome(event: Event): Boolean = event.location exists (_ equalsIgnoreCase "home")
}
