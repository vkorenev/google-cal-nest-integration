package controllers

import javax.inject.{Inject, Singleton}

import models.nest.{NestApi, NestConfig}
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MainController @Inject() (
  ws: WSClient,
  configuration: Configuration,
  nestApi: NestApi)(implicit exec: ExecutionContext) extends Controller {

  private val nestAccessTokenSessionKey = "nest-access-token"

  private lazy val nestProductId = configuration.getString("nest.auth.productId", None) getOrElse {
    throw new Exception("You should configure Nest Product ID in /conf/auth.conf")
  }
  private lazy val nestProductSecret = configuration.getString("nest.auth.productSecret", None) getOrElse {
    throw new Exception("You should configure Nest Product Secret in /conf/auth.conf")
  }

  def selectStructure = Action.async { request =>
    request.session.get(nestAccessTokenSessionKey) map { accessToken =>
      nestApi.withNest(accessToken) { rootRef =>
        nestApi.getStructures(rootRef) map { structures =>
          if (structures.size > 1) {
            Ok(views.html.selectStructure(structures))
          } else {
            Redirect(routes.MainController.setStructure(structures.head.id))
          }
        }
      }
    } getOrElse {
      Future.successful(nestAuthRedirect("state")) // TODO Implement CSRF protection
    }
  }

  private[this] def nestAuthRedirect(state: String) =
    Redirect(NestConfig.authUrl, Map(
      "client_id" -> Seq(nestProductId),
      "state" -> Seq(state)))

  def setStructure(structureId: String) = Action {
    Ok(structureId)
  }

  def receiveNestAuthCode(code: String) = Action.async {
    ws.url(NestConfig.tokenUrl).post(Map(
      "client_id" -> Seq(nestProductId),
      "code" -> Seq(code),
      "client_secret" -> Seq(nestProductSecret),
      "grant_type" -> Seq("authorization_code"))) map { response =>
      val accessToken = (response.json \ "access_token").as[String]
      Redirect(routes.MainController.selectStructure()).withSession(nestAccessTokenSessionKey -> accessToken)
    }
  }
}
