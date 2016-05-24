package controllers

import javax.inject.{Inject, Singleton}

import com.firebase.client.Firebase
import models.nest.{NestConfig, Structure}
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}
import vkorenev.firebase.ScalaFirebase

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MainController @Inject() (
  ws: WSClient,
  configuration: Configuration)(implicit exec: ExecutionContext) extends Controller {

  private val nestAccessTokenSessionKey = "nest-access-token"

  private lazy val nestProductId = configuration.getString("nest.auth.productId", None) getOrElse {
    throw new Exception("You should configure Nest Product ID in /conf/auth.conf")
  }
  private lazy val nestProductSecret = configuration.getString("nest.auth.productSecret", None) getOrElse {
    throw new Exception("You should configure Nest Product Secret in /conf/auth.conf")
  }

  def index = Action.async { request =>
    request.session.get(nestAccessTokenSessionKey) map { accessToken =>
      val rootRef = new Firebase(NestConfig.apiUrl)
      for {
        authData <- rootRef.authWithCustomToken(accessToken)
        structuresRef = rootRef.child("structures")
        structuresData <- structuresRef.getSingleValue
        structures = structuresData.getChildren.asScala map { structureData =>
          val id = structureData.child("structure_id").getValue.asInstanceOf[String]
          val name = structureData.child("name").getValue.asInstanceOf[String]
          Structure(id, name)
        }
      } yield Ok(views.html.index(structures))
    } getOrElse {
      Future.successful(nestAuthRedirect("state")) // TODO Implement CSRF protection
    }
  }

  private[this] def nestAuthRedirect(state: String) =
    Redirect(NestConfig.authUrl, Map(
      "client_id" -> Seq(nestProductId),
      "state" -> Seq(state)))

  def receiveNestAuthCode(code: String) = Action.async {
    ws.url(NestConfig.tokenUrl).post(Map(
      "client_id" -> Seq(nestProductId),
      "code" -> Seq(code),
      "client_secret" -> Seq(nestProductSecret),
      "grant_type" -> Seq("authorization_code"))) map { response =>
      val accessToken = (response.json \ "access_token").as[String]
      Redirect(routes.MainController.index()).withSession(nestAccessTokenSessionKey -> accessToken)
    }
  }
}
