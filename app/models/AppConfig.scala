package models

import java.time.Duration
import javax.inject.{Inject, Singleton}

import play.api.Configuration

@Singleton
class AppConfig @Inject() (configuration: Configuration) {
  private[this] val config = configuration.underlying
  lazy val etaWindowBeginsBeforeEventStart: Duration = config.getDuration("app.etaWindowBeginsBeforeEventStart")
  lazy val etaWindowEndsBeforeEventStart: Duration = config.getDuration("app.etaWindowEndsBeforeEventStart")
  lazy val upcomingEventsWindowBeginsIn: Duration = config.getDuration("app.upcomingEventsWindowBeginsIn")
  lazy val upcomingEventsWindowEndsIn: Duration = config.getDuration("app.upcomingEventsWindowEndsIn")
  lazy val updateInterval: Duration = config.getDuration("app.updateInterval")
  lazy val minDurationToArrive: Duration = config.getDuration("app.minDurationToArrive")
}
