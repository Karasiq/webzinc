package com.karasiq.webzinc.model

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

trait WebResource {
  def url: String
  def dataStream: Source[ByteString, NotUsed]
}

object WebResource {
  trait Static extends WebResource {
    def data: ByteString
    def dataStream = Source.single(data)
  }
}
