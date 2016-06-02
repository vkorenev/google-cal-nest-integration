package models

import models.google.calendar.Event

object AppLogic {
  def isAtHome(event: Event): Boolean = event.location exists (_ equalsIgnoreCase "home")
}
