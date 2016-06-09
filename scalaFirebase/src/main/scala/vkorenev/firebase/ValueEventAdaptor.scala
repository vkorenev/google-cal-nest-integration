package vkorenev.firebase

import com.firebase.client.{DataSnapshot, FirebaseError, ValueEventListener}

import scala.concurrent.Promise

private[firebase] class ValueEventAdaptor extends ValueEventListener {
  private[this] val promise = Promise[DataSnapshot]

  def future = promise.future

  override def onDataChange(snapshot: DataSnapshot) = promise.success(snapshot)

  override def onCancelled(error: FirebaseError) = promise.failure(FirebaseException(error))
}
