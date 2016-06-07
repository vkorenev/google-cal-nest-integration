package services

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import models.AppConfig
import models.google.auth.{GoogleAuth, RefreshableToken}
import play.api.Logger
import utils.JavaConversions.toFiniteDuration
import utils.NonCachingThrottle

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Singleton
class UpdateScheduler @Inject() (
  appConfig: AppConfig,
  googleAuth: GoogleAuth,
  calendarHelper: CalendarHelper,
  updateHelper: ETAUpdateHelper)(implicit exec: ExecutionContext, materializer: Materializer) {

  import appConfig._

  private val logger: Logger = Logger(this.getClass)

  def scheduleCheckUpcomingEvents(googleClientId: String, googleClientSecret: String,
    googleToken: RefreshableToken, calendarIds: Iterable[String], nestAccessToken: String, structureId: String) = {
    googleAuth.accessTokenSource(googleClientId, googleClientSecret, googleToken)
      .via(new NonCachingThrottle(updateInterval))
      .mapAsyncUnordered(1) { accessToken =>
        calendarHelper.getUpcomingEventsAtHome(accessToken, calendarIds)
      }
      .mapConcat(_.to[collection.immutable.Iterable])
      .runForeach { event =>
        updateHelper.updateETAForEvent(nestAccessToken, structureId, event) onComplete {
          case Success(result) => logger.debug(s"ETA was reported for an event")
          case Failure(error) => logger.warn("Error updating ETA", error)
        }
      }
  }
}
