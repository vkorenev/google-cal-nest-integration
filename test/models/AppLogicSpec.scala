package models

import java.time.OffsetDateTime

import models.AppLogic._
import models.google.calendar.TimedEvent
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AppLogicSpec extends Specification {
  val time = OffsetDateTime.parse("2016-01-01T00:00:00+03:00")

  "isAtHome" should {
    "return true for events at home" in {
      isAtHome(TimedEvent("id", "summary", None, Some("home"), time, time)) must beTrue
      isAtHome(TimedEvent("id", "summary", None, Some("Home"), time, time)) must beTrue
      isAtHome(TimedEvent("id", "summary", None, Some("HOME"), time, time)) must beTrue
    }

    "return false for events for events not at home" in {
      isAtHome(TimedEvent("id", "summary", None, Some("work"), time, time)) must beFalse
      isAtHome(TimedEvent("id", "summary", None, Some("Mike's home"), time, time)) must beFalse
    }

    "return false for events with no location specified" in {
      isAtHome(TimedEvent("id", "summary", None, None, time, time)) must beFalse
    }
  }
}
