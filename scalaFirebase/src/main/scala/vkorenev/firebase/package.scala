package vkorenev

import com.firebase.client.{AuthData, DataSnapshot, Firebase}

import scala.collection.JavaConverters._
import scala.concurrent.Future

package object firebase {
  implicit class ScalaFirebase(val underlying: Firebase) extends AnyVal {
    def authWithCustomToken(token: String): Future[AuthData] = {
      val handler = new AuthResultAdaptor
      underlying.authWithCustomToken(token, handler)
      handler.future
    }

    def getSingleValue: Future[DataSnapshot] = {
      val listener = new ValueEventAdaptor
      underlying.addListenerForSingleValueEvent(listener)
      listener.future
    }

    def updateChildren(children: Map[String, AnyRef]): Future[Firebase] = {
      val listener = new CompletionAdaptor
      underlying.updateChildren(children.asJava, listener)
      listener.future
    }
  }
}
