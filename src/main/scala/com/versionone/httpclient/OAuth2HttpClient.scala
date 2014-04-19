package com.versionone.httpclient

import scala.util._

import org.apache.oltu.oauth2.client._
import org.apache.oltu.oauth2.common.token._
import org.apache.oltu.oauth2.client.request._
import org.apache.oltu.oauth2.client.response._
import org.apache.oltu.oauth2.common.message.types._

/**
 * Basic HTTP functionality
 */
trait HttpClient {
  /**
   * Perform a request to a relative path.
   * A GET is performed unless if the postBody is null, otherwise a POST is performed with the given body.
   * Returned is a tuple of the response status, body, and a function that can fetch the ?params? (headers?).
   */
  def DoRequest(pathSuffix: String, postBody: String): (Int, String, String => String)
}

/**
 * A simple log interface we use instead of requiring
 * the user to import a logging library of our choice.
 */
trait SimpleLogger {
  def debug(s: String): Unit
  def info(s: String): Unit
  def error(s: String): Unit
}

/**
 * An OAuth2 refresh-capable HTTP client based on Apache Oltu
 * It will refresh the auth token once upon reception of a 401, then retry the request.
 * If made, the second try is returned without interception.
 * This implementation does not persistently store the refreshed token, but will reuse it for the lifetime of the instance.
 */
class OAuth2HttpClient(settings: OAuth2Settings, log: SimpleLogger, userAgent: String) extends HttpClient {
  val oAuthClient = new OAuthClient(new URLConnectionClient())
  log debug s"new OAuth2HttpClient($settings, $userAgent)"
  var creds: OAuthToken = settings.creds
  log debug "Initial OAuth2 creds set from provided settings"

  private def TryHttp(creds: OAuthToken, method: String, absUrl: String, body: String) = {
    log info s"TryHttp $method $absUrl ${if (body != null) s"body:\n$body" else ""}"
    if (body != null)
      log debug s"TryHttp body:\n$body"
    val bearerClientRequest =
      new OAuthBearerClientRequest(absUrl)
        .setAccessToken(creds.getAccessToken())
        .buildHeaderMessage()
    bearerClientRequest setHeader ("User-Agent", userAgent)
    if (method == "POST") {
      bearerClientRequest.setBody(if (body == null) "" else body)
    }
    log debug s"about to make request ${(bearerClientRequest.getLocationUri(), bearerClientRequest.getHeaders(), bearerClientRequest.getBody())}"
    val response = oAuthClient.resource(bearerClientRequest, method, classOf[OAuthResourceResponse])
    log info s"TryHttp resp code ${response.getResponseCode()}."
    log debug s"TryHttp resp body:\n${response.getBody()}"
    (response.getResponseCode(), response.getBody(), response.getParam(_))
  }

  def DoRequest(pathSuffix: String, postBody: String) = {
    val baseUrl =
      if (settings.baseUri.endsWith("/"))
        settings.baseUri.substring(0, settings.baseUri.length() - 1)
      else
        settings.baseUri
    val absUrl = baseUrl + "/" + pathSuffix
    val method = if (postBody == null) "GET" else "POST"

    val resp @ (status, _, _) = TryHttp(creds, method, absUrl, postBody)
    if (status != 401)
      resp
    else {
      log info "Refreshing access token..."
      val request =
        OAuthClientRequest
          .tokenLocation(settings.tokenUri)
          .setGrantType(GrantType.REFRESH_TOKEN)
          .setClientId(settings.clientId)
          .setClientSecret(settings.clientSecret)
          .setRedirectURI(settings.redirectUri)
          .setRefreshToken(creds.getRefreshToken())
          .buildBodyMessage()
      creds = oAuthClient.accessToken(request).getOAuthToken()
      log info s"Refreshed. Valid for ${creds.getExpiresIn()}s. Retrying..."
      TryHttp(creds, method, absUrl, postBody)
    }
  }
}
