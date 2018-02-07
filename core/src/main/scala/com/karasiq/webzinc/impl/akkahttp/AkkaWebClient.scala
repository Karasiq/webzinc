package com.karasiq.webzinc.impl.akkahttp

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer

import com.karasiq.webzinc.WebClient

object AkkaWebClient {
  def apply()(implicit as: ActorSystem, mat: Materializer): AkkaWebClient = {
    new AkkaWebClient
  }
}

final class AkkaWebClient(implicit as: ActorSystem, mat: Materializer) extends WebClient {
  private[this] val http = Http()

  def doHttpRequest(url: String): Future[HttpResponse] = {
    http.singleRequest(HttpRequest(uri = url))
  }
}
