package models.google.auth

import java.time.{Clock, Duration, Instant, ZoneOffset}

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.{Action, Results}
import play.api.routing.sird._
import utils.test.ws.WSTestHelper._

class GoogleAuthSpec(implicit ee: ExecutionEnv) extends Specification {
  val now = Instant.parse("2000-01-01T00:00:00Z")
  val clock = Clock.fixed(now, ZoneOffset.UTC)
  val clientId = "client_id"
  val clientSecret = "client_secret"
  val redirectUri = "http://host/receiveCode"

  "GoogleAuth" should {
    "get refreshable token" in {
      val code = "Code"
      val accessToken = "AccessToken"
      val refreshToken = "RefreshToken"
      val expiresInSeconds = 3920

      withRouter {
        case POST(p"https://www.googleapis.com/oauth2/v4/token") => Action { request =>
          request.body.asFormUrlEncoded must beSome(havePairs(
            "code" -> Seq(code),
            "client_id" -> Seq(clientId),
            "client_secret" -> Seq(clientSecret),
            "redirect_uri" -> Seq(redirectUri),
            "grant_type" -> Seq("authorization_code")))
          Results.Ok(Json.obj(
            "access_token" -> accessToken,
            "expires_in" -> expiresInSeconds,
            "token_type" -> "Bearer",
            "refresh_token" -> refreshToken))
        }
      } { implicit port =>
        withClient { ws =>
          val googleAuth = new GoogleAuth(ws, clock)
          val expiresAtMillis = now.plus(Duration.ofSeconds(expiresInSeconds)).toEpochMilli
          googleAuth.getRefreshableToken(clientId, clientSecret)(code, redirectUri) must beEqualTo(
            RefreshableToken(accessToken, refreshToken, expiresAtMillis)).await
        }
      }
    }

    "refresh token" in {
      val accessToken = "AccessToken"
      val refreshToken = "RefreshToken"
      val expiresInSeconds = 3600

      withRouter {
        case POST(p"https://www.googleapis.com/oauth2/v4/token") => Action { request =>
          request.body.asFormUrlEncoded must beSome(havePairs(
            "refresh_token" -> Seq(refreshToken),
            "client_id" -> Seq(clientId),
            "client_secret" -> Seq(clientSecret),
            "grant_type" -> Seq("refresh_token")))
          Results.Ok(Json.obj(
            "access_token" -> accessToken,
            "expires_in" -> expiresInSeconds,
            "token_type" -> "Bearer"))
        }
      } { implicit port =>
        withClient { ws =>
          val googleAuth = new GoogleAuth(ws, clock)
          val expiresAtMillis = now.plus(Duration.ofSeconds(expiresInSeconds)).toEpochMilli
          val token = RefreshableToken("OldToken", refreshToken, now.toEpochMilli)
          googleAuth.refreshIfNecessary(clientId, clientSecret)(token) must beEqualTo(
            RefreshableToken(accessToken, refreshToken, expiresAtMillis)).await
        }
      }
    }

    "fail on error when getting refreshable token" in {
      val description = "invalid request"

      withRouter {
        case POST(p"https://www.googleapis.com/oauth2/v4/token") => Action { request =>
          Results.BadRequest(Json.obj(
            "error" -> "invalid_request",
            "error_description" -> description))
        }
      } { implicit port =>
        withClient { ws =>
          val googleAuth = new GoogleAuth(ws, clock)
          googleAuth.getRefreshableToken(clientId, clientSecret)("Code", redirectUri) must
            throwA[AuthException](description).await
        }
      }
    }

    "fail on error when refreshing token" in {
      val description = "invalid request"

      withRouter {
        case POST(p"https://www.googleapis.com/oauth2/v4/token") => Action { request =>
          Results.BadRequest(Json.obj(
            "error" -> "invalid_request",
            "error_description" -> description))
        }
      } { implicit port =>
        withClient { ws =>
          val token = RefreshableToken("OldToken", "RefreshToken", now.toEpochMilli)
          val googleAuth = new GoogleAuth(ws, clock)
          googleAuth.refreshIfNecessary(clientId, clientSecret)(token) must throwA[AuthException](description).await
        }
      }
    }
  }
}
