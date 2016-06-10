package services

import java.time._

import com.firebase.client.Firebase
import models.google.calendar.TimedEvent
import models.nest.NestApi
import org.junit.runner.RunWith
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.concurrent.Future
import scala.language.existentials

@RunWith(classOf[JUnitRunner])
class ETAUpdateHelperSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  val now = Instant.parse("2000-01-01T00:00:00Z")
  val clock = Clock.fixed(now, ZoneOffset.UTC)
  val nestAccessToken = "nest_token"
  val structureId = "structure_id"

  val etaWindowBeginsBeforeEventStart = Duration.ofMinutes(15)
  val etaWindowEndsBeforeEventStart = Duration.ofMinutes(5)
  val minDurationToArrive = Duration.ofMinutes(2)
  val appConfig = mock[AppConfig]
  appConfig.etaWindowBeginsBeforeEventStart returns etaWindowBeginsBeforeEventStart
  appConfig.etaWindowEndsBeforeEventStart returns etaWindowEndsBeforeEventStart
  appConfig.minDurationToArrive returns minDurationToArrive

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

  "ETAUpdateHelper" should {

    "report ETA to nest" in {
      val eventStart = Instant.parse("2016-01-01T00:00:00Z")
      val eventEnd = eventStart.plus(Duration.ofHours(1))
      val event = TimedEvent("id", "", None, Some("home"),
        eventStart.atOffset(ZoneOffset.UTC), eventEnd.atOffset(ZoneOffset.UTC))

      val nestApi = nestApiMock()

      val updateResult = mock[Future[Firebase]]
      nestApi.updateETA(any[Firebase], any[String], any[String], any[Instant], any[Instant]) returns updateResult

      val etaUpdateHelper = new ETAUpdateHelper(clock, appConfig, nestApi)
      etaUpdateHelper.updateETAForEvent(nestAccessToken, structureId, event) must beTheSameAs(updateResult)
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

      val etaUpdateHelper = new ETAUpdateHelper(clock, appConfig, nestApi)
      etaUpdateHelper.updateETAForEvent(nestAccessToken, structureId, event) must beTheSameAs(updateResult)
      there was one(nestApi).updateETA(firebase, structureId, event.id,
        now.plus(minDurationToArrive),
        now.plus(minDurationToArrive))
    }
  }
}
