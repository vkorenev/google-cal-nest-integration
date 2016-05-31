package vkorenev.firebase

import com.firebase.client.{Firebase, FirebaseError}

import scala.concurrent.Promise

private[firebase] class CompletionAdaptor extends Firebase.CompletionListener {
  private[this] val promise = Promise[Firebase]

  def future = promise.future

  override def onComplete(error: FirebaseError, ref: Firebase) =
    if (error == null)
      promise.success(ref)
    else
      promise.failure(FirebaseException(error))
}
