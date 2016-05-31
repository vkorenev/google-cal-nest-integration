package models.google.calendar

import java.time.{LocalDate, OffsetDateTime}

sealed trait Event {
  val id: String
  val summary: String
  val description: Option[String]
  val location: Option[String]
}

case class AllDayEvent(
  id: String,
  summary: String,
  description: Option[String],
  location: Option[String],
  start: LocalDate,
  end: LocalDate) extends Event

case class TimedEvent(
  id: String,
  summary: String,
  description: Option[String],
  location: Option[String],
  start: OffsetDateTime,
  end: OffsetDateTime) extends Event
