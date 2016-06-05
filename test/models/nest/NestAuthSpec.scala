package models.nest

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.{Action, Results}
import play.api.routing.sird._
import utils.test.ws.WSTestHelper._

class NestAuthSpec(implicit ee: ExecutionEnv) extends Specification {
  val productId = "product_id"
  val productSecret = "product_secret"
  val code = "123"

  "NestAuth" should {
    "get access token" in {
      val accessToken = "456"

      withRouter {
        case POST(p"https://api.home.nest.com/oauth2/access_token") => Action { request =>
          request.body.asFormUrlEncoded must beSome(havePairs(
            "client_id" -> Seq(productId),
            "code" -> Seq(code),
            "client_secret" -> Seq(productSecret),
            "grant_type" -> Seq("authorization_code")))
          Results.Ok(Json.obj(
            "access_token" -> accessToken,
            "expires_in" -> 123456))
        }
      } { implicit port =>
        withClient { ws =>
          val nestAuth = new NestAuth(ws)
          nestAuth.getAccessToken(productId, productSecret)(code) must beEqualTo(accessToken).await
        }
      }
    }

    "fail on error" in {
      val description = "authorization code expired"

      withRouter {
        case POST(p"https://api.home.nest.com/oauth2/access_token") => Action { request =>
          Results.BadRequest(Json.obj(
            "error" -> "oauth2_error",
            "error_description" -> description))
        }
      } { implicit port =>
        withClient { ws =>
          val nestAuth = new NestAuth(ws)
          nestAuth.getAccessToken(productId, productSecret)(code) must throwA[AuthException](description).await
        }
      }
    }
  }
}
