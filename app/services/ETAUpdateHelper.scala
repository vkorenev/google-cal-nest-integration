package services

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}

import models.google.calendar.TimedEvent
import models.nest.NestApi
import play.api.Logger
import utils.JavaConversions.instantOrdering._

import scala.concurrent.Future

@Singleton
class ETAUpdateHelper @Inject() (
  clock: Clock,
  appConfig: AppConfig,
  nestApi: NestApi) {
  import appConfig._

  private val logger: Logger = Logger(this.getClass)

  def updateETAForEvent(accessToken: String, structureId: String, event: TimedEvent): Future[_] = {
    nestApi.withNest(accessToken) { rootRef =>
      val willArriveNotBefore = Instant.now(clock).plus(minDurationToArrive)
      val eventStart = event.start.toInstant
      val windowBegin = max(willArriveNotBefore, eventStart.minus(etaWindowBeginsBeforeEventStart))
      val windowEnd = max(willArriveNotBefore, eventStart.minus(etaWindowEndsBeforeEventStart))
      logger.debug(s"Setting ETA for structure $structureId from $windowBegin to $windowEnd because of $event")
      nestApi.updateETA(rootRef, structureId, event.id, windowBegin, windowEnd)
    }
  }
}
