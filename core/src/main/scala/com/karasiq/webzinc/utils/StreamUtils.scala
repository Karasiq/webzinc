package com.karasiq.webzinc.utils

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import com.karasiq.webzinc.config.WebZincConfig

private[webzinc] object StreamUtils {
  type BytesFlow = Flow[ByteString, ByteString, NotUsed]

  def limitBytes(limit: Long): BytesFlow = {
    Flow[ByteString]
      .statefulMapConcat { () ⇒
        var read = 0L
        bytes ⇒ {
          read += bytes.length
          if (read > limit) throw new RuntimeException("Read limit exceeded")
          bytes :: Nil
        }
      }
      .named("limitBytes")
  }

  def applyConfig(implicit config: WebZincConfig): BytesFlow = {
    Flow[ByteString]
      .via(limitBytes(config.fileSizeLimit))
      .idleTimeout(config.readTimeout)
  }
}
