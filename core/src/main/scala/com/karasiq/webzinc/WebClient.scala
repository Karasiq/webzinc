package com.karasiq.webzinc

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.karasiq.webzinc.impl.akkahttp.AkkaWebClient

trait WebClient {
  def openDataStream(url: String): Source[ByteString, NotUsed]
}

object WebClient {
  def apply()(implicit as: ActorSystem, mat: Materializer): WebClient = {
    AkkaWebClient()
  }
}
