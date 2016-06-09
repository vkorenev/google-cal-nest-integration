package vkorenev.firebase

import com.firebase.client.{AuthData, Firebase, FirebaseError}

import scala.concurrent.Promise

private[firebase] class AuthResultAdaptor extends Firebase.AuthResultHandler {
  private[this] val promise = Promise[AuthData]

  def future = promise.future

  override def onAuthenticated(authData: AuthData) = promise.success(authData)

  override def onAuthenticationError(error: FirebaseError) = promise.failure(FirebaseException(error))
}
