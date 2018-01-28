package com.karasiq.webzinc.impl.htmlunit

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import com.gargoylesoftware.htmlunit.Page

import com.karasiq.networkutils.HtmlUnitUtils.newWebClient
import com.karasiq.webzinc.WebClient
import com.karasiq.webzinc.utils.StreamAttrs

object HtmlUnitWebClient {
  def apply(): HtmlUnitWebClient = {
    new HtmlUnitWebClient
  }
}

class HtmlUnitWebClient extends WebClient {
  protected val webClient = newWebClient(js = false)

  def openDataStream(url: String): Source[ByteString, NotUsed] = {
    Source.single(url)
      .flatMapConcat { url ⇒
        val page = webClient.getPage[Page](url)
        StreamConverters.fromInputStream(() ⇒ page.getWebResponse.getContentAsStream)
          .alsoTo(Sink.onComplete(_ ⇒ page.cleanUp()))
      }
      .withAttributes(StreamAttrs.useBlockingDispatcher)
      .named("htmlUnitDataStream")
  }
}
