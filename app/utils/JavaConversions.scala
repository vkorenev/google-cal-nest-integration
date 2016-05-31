package utils

import java.time.Instant

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions

object JavaConversions {
  implicit def toFiniteDuration(d: java.time.Duration): FiniteDuration = Duration.fromNanos(d.toNanos)

  val instantOrdering = implicitly[Ordering[Instant]]
}
