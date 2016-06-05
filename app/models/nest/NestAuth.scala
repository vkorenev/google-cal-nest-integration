package models.nest

import javax.inject.Inject

import play.api.http.Status
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class NestAuth @Inject() (ws: WSClient)(implicit exec: ExecutionContext) {
  import NestAuth._

  def getAccessToken(nestProductId: String, nestProductSecret: String)(code: String): Future[String] =
    ws.url(tokenUrl).post(Map(
      "code" -> Seq(code),
      "client_id" -> Seq(nestProductId),
      "client_secret" -> Seq(nestProductSecret),
      "grant_type" -> Seq("authorization_code"))) map { response =>
      val json = response.json
      if (response.status == Status.OK) {
        (json \ "access_token").as[String]
      } else {
        val error = (json \ "error").asOpt[String]
        val errorDescription = (json \ "error_description").asOpt[String]
        val errorMessage = errorDescription.orElse(error).getOrElse("authorization error")
        throw AuthException(errorMessage)
      }
    }
}

object NestAuth {
  val authUrl = "https://home.nest.com/login/oauth2"
  val tokenUrl = "https://api.home.nest.com/oauth2/access_token"
}

case class AuthException(message: String) extends Exception(message)
