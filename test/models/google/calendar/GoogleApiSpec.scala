package models.google.calendar

import java.time.{LocalDate, OffsetDateTime}

import models.google.calendar.GoogleApi._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class GoogleApiSpec extends Specification {
  "Calendar" should {
    "be parsed from JSON" in {
      val json = Json.parse(getClass.getResourceAsStream("calendarList.list.json"))
      val events = (json \ "items").as[Seq[Calendar]]
      events must have size 4
      events must containTheSameElementsAs(Seq(
        Calendar("lrt8dr2sh6tksatg5pi31d6daseeqavc@import.calendar.google.com", false,
          "http://people.ubuntu.com/~vorlon/UbuntuReleaseSchedule.ics", Some("Ubuntu Release Schedule"), None),
        Calendar("d0nqdreuqe3mepm8f7l8cdm20i6krqia@import.calendar.google.com", false,
          "Principles of Reactive Programming", None, None),
        Calendar("google.com_jqv7qt9iifsaj94cuknckrabd8@group.calendar.google.com", false,
          "Google Code Jam", None, Some("Public calendar of important dates and events for Google Code Jam competitions.")),
        Calendar("vlohs42usbeflp41kctrevhnb0@group.calendar.google.com", true,
          "Nest Test", None, Some("Events for testing an application which sends ETA to Nest"))))
    }
  }

  "Event" should {
    "be parsed from JSON" in {
      val json = Json.parse(getClass.getResourceAsStream("events.list.json"))
      val events = (json \ "items").as[Seq[Event]]
      events must have size 4
      events must containTheSameElementsAs(Seq(
        TimedEvent("buuoboldl0988holrp73o17m7o", "Lunch", None, None,
          OffsetDateTime.parse("2016-05-23T12:30+03:00"), OffsetDateTime.parse("2016-05-23T13:30+03:00")),
        AllDayEvent("egg1igbutuqhpgd0hrrei03t6k", "Somebody's birthday", None, None,
          LocalDate.parse("2016-05-28"), LocalDate.parse("2016-05-29")),
        AllDayEvent("rv7e8d9ms0r1er0enoflp6eb1g", "Staying at home", None, Some("home"),
          LocalDate.parse("2016-05-29"), LocalDate.parse("2016-05-30")),
        TimedEvent("i53dpld9bo2afn9an2a97njt5s", "Breakfast at home", Some("Cheese omelette"), Some("home"),
          OffsetDateTime.parse("2016-05-25T09:00+03:00"), OffsetDateTime.parse("2016-05-25T10:00+03:00"))))
    }
  }
}
