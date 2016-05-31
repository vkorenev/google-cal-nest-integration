package models.google.calendar

case class Calendar(
  id: String,
  primary: Boolean,
  summary: String,
  summaryOverride: Option[String],
  description: Option[String])
