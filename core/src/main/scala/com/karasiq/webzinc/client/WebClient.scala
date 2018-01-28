package com.karasiq.webzinc.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

trait WebClient {
  def openDataStream(url: String): Source[ByteString, NotUsed]
}

object WebClient {
  def apply()(implicit as: ActorSystem, mat: Materializer): WebClient = {
    AkkaWebClient()
  }
}
