package controllers

import javax.inject.{Inject, Singleton}

import models.EventScheduler
import models.google.auth.GoogleAuth
import models.google.calendar.{GoogleApi, GoogleConfig}
import models.nest.{NestApi, NestAuth}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MainController @Inject() (
  configuration: Configuration,
  nestAuth: NestAuth,
  nestApi: NestApi,
  googleAuth: GoogleAuth,
  googleApi: GoogleApi,
  eventScheduler: EventScheduler)(implicit exec: ExecutionContext) extends Controller {

  private val nestAccessTokenSessionKey = "nest-access-token"
  private val nestStructureIdSessionKey = "nest-structure-id"

  private lazy val nestProductId = configuration.getString("nest.auth.productId", None) getOrElse {
    throw new Exception("You should configure Nest Product ID in /conf/auth.conf")
  }
  private lazy val nestProductSecret = configuration.getString("nest.auth.productSecret", None) getOrElse {
    throw new Exception("You should configure Nest Product Secret in /conf/auth.conf")
  }
  private lazy val googleClientId = configuration.getString("google.auth.clientId", None) getOrElse {
    throw new Exception("You should configure Google Client ID in /conf/auth.conf")
  }
  private lazy val googleClientSecret = configuration.getString("google.auth.clientSecret", None) getOrElse {
    throw new Exception("You should configure Google Client Secret in /conf/auth.conf")
  }

  def index = Action {
    Ok(views.html.index())
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
    Redirect(NestAuth.authUrl, Map(
      "client_id" -> Seq(nestProductId),
      "state" -> Seq(state)))

  def setStructure(structureId: String) = Action { request =>
    googleAuthRedirect(None, GoogleConfig.calendarReadonlyScope)
      .withSession(request.session + (nestStructureIdSessionKey -> structureId))
  }

  private[this] def googleAuthRedirect(state: Option[String], scopes: String*) =
    Redirect(GoogleAuth.authUrl, Map(
      "response_type" -> Seq("code"),
      "client_id" -> Seq(googleClientId),
      "redirect_uri" -> Seq("http://localhost:9000/receiveGoogleAuthCode"),
      "scope" -> Seq(scopes.mkString(" ")),
      "state" -> state.toSeq,
      "prompt" -> Seq("consent"),
      "access_type" -> Seq("offline")))

  val calendarsForm = Form(single("calendars" -> seq(text)))

  def receiveGoogleAuthCode(code: String) = Action.async { request =>
    for {
      refreshableToken <- googleAuth.getRefreshableToken(googleClientId, googleClientSecret)(code,
        "http://localhost:9000/receiveGoogleAuthCode")
      accessToken = refreshableToken.accessToken
      calendars <- googleApi.getCalendars(accessToken)
    } yield Ok(views.html.selectCalendars(calendars)).withSession(request.session + ("google_access_token" -> accessToken))
  }

  def setCalendars = Action { implicit request =>
    request.session.get("google_access_token") map { googleAccessToken =>
      calendarsForm.bindFromRequest.fold(errors => {
        BadRequest
      }, calendarIds => {
        calendarIds foreach { calendarId =>
          val nestAccessToken = request.session.get(nestAccessTokenSessionKey).get
          val structureId = request.session.get(nestStructureIdSessionKey).get
          eventScheduler.scheduleCheckUpcomingEvents(googleAccessToken, calendarId, nestAccessToken, structureId)
        }
        Ok(views.html.calendarsImportFinished(calendarIds.size)).withNewSession
      })
    } getOrElse {
      googleAuthRedirect(None, GoogleConfig.calendarReadonlyScope)
    }
  }

  def receiveNestAuthCode(code: String) = Action.async {
    nestAuth.getAccessToken(nestProductId, nestProductSecret)(code) map { accessToken =>
      Redirect(routes.MainController.selectStructure()).withSession(nestAccessTokenSessionKey -> accessToken)
    }
  }
}
