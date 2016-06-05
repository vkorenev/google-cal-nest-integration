package utils.test.ws

import java.net.URLDecoder

import play.api.http.Port
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.{Handler, RequestHeader}
import play.api.test.WsTestClient
import play.core.server.{Server, ServerProvider}

object WSTestHelper {
  private val prefix: String = "/$"
  private val encoding = "UTF-8"

  def withRouter[T](routes: PartialFunction[RequestHeader, Handler])(block: Port => T)(implicit provider: ServerProvider) = {
    def canDecode(uri: String) = uri startsWith prefix

    def decode(uri: String) = URLDecoder.decode(uri.substring(prefix.length), encoding)

    def decodeUri(requestHeader: RequestHeader): RequestHeader = requestHeader.copy(
      uri = decode(requestHeader.uri),
      path = decode(requestHeader.path))

    Server.withRouter()(new PartialFunction[RequestHeader, Handler] {
      override def isDefinedAt(requestHeader: RequestHeader) =
        canDecode(requestHeader.uri) && routes.isDefinedAt(decodeUri(requestHeader))

      override def apply(requestHeader: RequestHeader) = routes.apply(decodeUri(requestHeader))
    })(block)
  }

  def withClient[T](block: WSClient => T)(implicit port: Port) =
    WsTestClient.withClient { ws =>
      block(new WSClient {
        override def underlying[A]: A = ws.underlying[A]

        override def url(url: String): WSRequest = {
          val encUrl = java.net.URLEncoder.encode(url, encoding)
          ws.url(prefix + encUrl)
        }

        override def close(): Unit = ws.close()
      })
    }
}
