package models

import java.time.{Clock, Instant}
import javax.inject.Inject

import akka.actor.ActorSystem
import models.AppLogic.isAtHome
import models.google.calendar.{GoogleApi, TimedEvent}
import models.nest.NestApi
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import utils.JavaConversions.instantOrdering._
import utils.JavaConversions.toFiniteDuration

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EventScheduler @Inject() (
  system: ActorSystem,
  clock: Clock,
  lifecycle: ApplicationLifecycle,
  appConfig: AppConfig,
  nestApi: NestApi,
  googleApi: GoogleApi)(implicit exec: ExecutionContext) {
  import appConfig._

  private val logger: Logger = Logger(this.getClass)

  private[models] def getUpcomingEventsAtHome(accessToken: String, calendarId: String): Future[Seq[TimedEvent]] = {
    val now = Instant.now(clock)
    val intervalStart = now.plus(upcomingEventsWindowBeginsIn)
    val intervalEnd = now.plus(upcomingEventsWindowEndsIn)

    googleApi.getEvents(accessToken)(calendarId, Some(intervalStart), Some(intervalEnd)) map { events =>
      for {
        event <- events.collect { case ev: TimedEvent => ev }
        if event.start.toInstant >= intervalStart && isAtHome(event)
      } yield event
    }
  }

  private[models] def updateETA(accessToken: String, structureId: String, event: TimedEvent): Future[_] = {
    nestApi.withNest(accessToken) { rootRef =>
      val willArriveNotBefore = Instant.now(clock).plus(minDurationToArrive)
      val eventStart = event.start.toInstant
      val windowBegin = max(willArriveNotBefore, eventStart.minus(etaWindowBeginsBeforeEventStart))
      val windowEnd = max(willArriveNotBefore, eventStart.minus(etaWindowEndsBeforeEventStart))
      logger.debug(s"Setting ETA for structure $structureId from $windowBegin to $windowEnd because of $event")
      nestApi.updateETA(rootRef, structureId, event.id, windowBegin, windowEnd)
    }
  }

  private def updateETAForUpcomingEvents(googleAccessToken: String, calendarId: String,
    nestAccessToken: String, structureId: String): Future[Int] = {
    for {
      events <- getUpcomingEventsAtHome(googleAccessToken, calendarId)
      results <- Future.sequence(events map { event =>
        updateETA(nestAccessToken, structureId, event)
      })
    } yield results.size
  }

  def scheduleCheckUpcomingEvents(googleAccessToken: String, calendarId: String,
    nestAccessToken: String, structureId: String) = {
    val cancellable = system.scheduler.schedule(Duration.Zero, updateInterval) {
      updateETAForUpcomingEvents(googleAccessToken, calendarId, nestAccessToken, structureId) onComplete {
        case Success(result) => logger.debug(s"ETA was reported for $result events")
        case Failure(error) => logger.warn("Error updating ETA", error)
      }
    }
    lifecycle.addStopHook { () =>
      Future.successful(cancellable.cancel())
    }
  }
}
