package com.karasiq.webzinc.impl.akkahttp

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.karasiq.webzinc.WebClient

object AkkaWebClient {
  def apply()(implicit as: ActorSystem, mat: Materializer): AkkaWebClient = {
    new AkkaWebClient
  }
}

class AkkaWebClient(implicit as: ActorSystem, mat: Materializer) extends WebClient {
  def openDataStream(url: String): Source[ByteString, NotUsed] = {
    import as.dispatcher
    Source.fromFutureSource(Http().singleRequest(HttpRequest(uri = url)).map(_.entity.dataBytes))
      .mapMaterializedValue(_ ⇒ NotUsed)
  }
}
