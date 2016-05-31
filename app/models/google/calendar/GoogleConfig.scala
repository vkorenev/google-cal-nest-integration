package models.google.calendar

object GoogleConfig {
  val authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
  val tokenUrl = "https://www.googleapis.com/oauth2/v4/token"
  val calendarReadonlyScope = "https://www.googleapis.com/auth/calendar.readonly"
}
