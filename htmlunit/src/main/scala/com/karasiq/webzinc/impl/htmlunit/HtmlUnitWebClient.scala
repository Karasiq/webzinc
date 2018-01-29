package com.karasiq.webzinc.impl.htmlunit

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.stream.scaladsl.{Sink, StreamConverters}
import com.gargoylesoftware.htmlunit.Page

import com.karasiq.networkutils.HtmlUnitUtils.newWebClient
import com.karasiq.webzinc.WebClient
import com.karasiq.webzinc.utils.StreamAttrs

object HtmlUnitWebClient {
  def apply()(implicit ec: ExecutionContext): HtmlUnitWebClient = {
    new HtmlUnitWebClient
  }
}

class HtmlUnitWebClient(implicit ec: ExecutionContext) extends WebClient {
  protected val webClient = newWebClient(js = false)

  def doHttpRequest(url: String): Future[HttpResponse] = Future {
    val page = webClient.getPage[Page](url)

    val statusCode = page.getWebResponse.getStatusCode
    val headers = page.getWebResponse.getResponseHeaders.asScala
      .map(p ⇒ HttpHeader.parse(p.getName, p.getValue))
      .collect { case ParsingResult.Ok(header, _) ⇒ header }
      .toVector

    val contentType = ContentType.parse(page.getWebResponse.getContentType).right.getOrElse(ContentTypes.`application/octet-stream`)
    val dataStream = StreamConverters.fromInputStream(() ⇒ page.getWebResponse.getContentAsStream)
      .alsoTo(Sink.onComplete(_ ⇒ page.cleanUp()))
      .withAttributes(StreamAttrs.useProvidedOrBlockingDispatcher)
      .named("htmlUnitDataStream")
    val entity = HttpEntity(contentType, page.getWebResponse.getContentLength, dataStream)

    HttpResponse(statusCode, headers, entity)
  }
}
