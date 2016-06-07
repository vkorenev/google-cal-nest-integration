package utils

import akka.event.Logging
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.concurrent.duration.{Deadline, FiniteDuration}

class NonCachingThrottle[T](delay: FiniteDuration) extends GraphStage[FlowShape[T, T]] {
  val in = Inlet[T](Logging.simpleName(this) + ".in")
  val out = Outlet[T](Logging.simpleName(this) + ".out")
  override val shape = FlowShape(in, out)

  private val timerName: String = "ThrottleTimer"

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {
      var deadline = Deadline.now

      override def onPush(): Unit = push(shape.out, grab(shape.in))

      override def onPull(): Unit = {
        if (deadline.hasTimeLeft()) {
          scheduleOnce(timerName, deadline.timeLeft)
        } else {
          doPull()
        }
      }

      private def doPull(): Unit = {
        pull(in)
        deadline += delay
      }

      override protected def onTimer(timerKey: Any): Unit = doPull()

      setHandler(in, this)
      setHandler(out, this)
    }
}
