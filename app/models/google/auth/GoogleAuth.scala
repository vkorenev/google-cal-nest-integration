package models.google.auth

import java.time.Clock
import javax.inject.Inject

import play.api.http.Status
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class GoogleAuth @Inject() (ws: WSClient, clock: Clock = Clock.systemUTC())(implicit exec: ExecutionContext) {
  import GoogleAuth._
  private[this] def millisFromNow(durationSeconds: Long) = clock.millis() + durationSeconds * 1000

  def getRefreshableToken(clientId: String, clientSecret: String)(code: String, redirectUri: String): Future[RefreshableToken] =
    ws.url(tokenUrl).post(Map(
      "code" -> Seq(code),
      "client_id" -> Seq(clientId),
      "client_secret" -> Seq(clientSecret),
      "redirect_uri" -> Seq(redirectUri),
      "grant_type" -> Seq("authorization_code"))) map { response =>
      val json = response.json
      if (response.status == Status.OK) {
        val accessToken = (json \ "access_token").as[String]
        val refreshToken = (json \ "refresh_token").as[String]
        val expiresIn = (json \ "expires_in").as[Long]
        val expiresAtMillis = millisFromNow(expiresIn)
        RefreshableToken(accessToken, refreshToken, expiresAtMillis)
      } else {
        throw error(json)
      }
    }

  def refreshIfNecessary(clientId: String, clientSecret: String)(refreshableToken: RefreshableToken): Future[RefreshableToken] =
    if (refreshableToken.expiresAtMillis - clock.millis() > refreshIfExpiresInMillis) {
      Future.successful(refreshableToken)
    } else {
      ws.url(tokenUrl).post(Map(
        "refresh_token" -> Seq(refreshableToken.refreshToken),
        "client_id" -> Seq(clientId),
        "client_secret" -> Seq(clientSecret),
        "grant_type" -> Seq("refresh_token"))) map { response =>
        val json = response.json
        if (response.status == Status.OK) {
          val accessToken = (json \ "access_token").as[String]
          val expiresIn = (json \ "expires_in").as[Long]
          val expiresAtMillis = millisFromNow(expiresIn)
          RefreshableToken(accessToken, refreshableToken.refreshToken, expiresAtMillis)
        } else {
          throw error(json)
        }
      }
    }

  private[this] def error(json: JsValue) = {
    val error = (json \ "error").asOpt[String]
    val errorDescription = (json \ "error_description").asOpt[String]
    val errorMessage = errorDescription.orElse(error).getOrElse("authorization error")
    AuthException(errorMessage)
  }
}

object GoogleAuth {
  val authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
  val tokenUrl = "https://www.googleapis.com/oauth2/v4/token"

  def authQueryParams(clientId: String, state: Option[String], scopes: String*) = Map(
    "response_type" -> Seq("code"),
    "client_id" -> Seq(clientId),
    "redirect_uri" -> Seq("http://localhost:9000/receiveGoogleAuthCode"),
    "scope" -> Seq(scopes.mkString(" ")),
    "state" -> state.toSeq,
    "prompt" -> Seq("consent"),
    "access_type" -> Seq("offline"))

  private[GoogleAuth] val refreshIfExpiresInMillis = 600000
}

case class RefreshableToken(accessToken: String, refreshToken: String, expiresAtMillis: Long)

case class AuthException(message: String) extends Exception(message)
