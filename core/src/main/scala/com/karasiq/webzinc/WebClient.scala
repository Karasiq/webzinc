package com.karasiq.webzinc

import scala.concurrent.Future

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.karasiq.webzinc.impl.akkahttp.AkkaWebClient

trait WebClient {
  def doHttpRequest(url: String): Future[HttpResponse]

  def openDataStream(url: String): Source[ByteString, NotUsed] = {
    Source.fromFuture(doHttpRequest(url))
      .flatMapConcat(_.entity.dataBytes)
      .named("httpDataStream")
  }
}

object WebClient {
  def apply()(implicit as: ActorSystem, mat: Materializer): WebClient = {
    AkkaWebClient()
  }
}
