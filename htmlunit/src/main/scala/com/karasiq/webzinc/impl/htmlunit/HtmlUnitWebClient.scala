package com.karasiq.webzinc.impl.htmlunit

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.stream.scaladsl.StreamConverters
import com.gargoylesoftware.htmlunit
import com.gargoylesoftware.htmlunit.{HttpHeader => _, HttpMethod => _, _}
import com.karasiq.common.ThreadLocalFactory
import com.karasiq.networkutils.HtmlUnitUtils.newWebClient
import com.karasiq.webzinc.WebClient
import com.karasiq.webzinc.utils.StreamAttrs

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object HtmlUnitWebClient {
  def apply()(implicit ec: ExecutionContext): HtmlUnitWebClient = {
    new HtmlUnitWebClient
  }
}

class HtmlUnitWebClient(implicit ec: ExecutionContext) extends WebClient {
  protected val cache = new Cache
  cache.setMaxSize(200)

  protected val webClient = ThreadLocalFactory(newWebClient(js = false, cache = cache), (_: htmlunit.WebClient).close())

  def doHttpRequest(url: String): Future[HttpResponse] = Future {
    val page = webClient().getPage[Page](url)

    val statusCode = page.getWebResponse.getStatusCode
    val headers = page.getWebResponse.getResponseHeaders.asScala
      .map(p ⇒ HttpHeader.parse(p.getName, p.getValue))
      .collect { case ParsingResult.Ok(header, _) ⇒ header }
      .toVector

    val contentType = ContentType.parse(page.getWebResponse.getContentType).right.getOrElse(ContentTypes.`application/octet-stream`)
    val entityStream = StreamConverters
      .fromInputStream(() ⇒ page.getWebResponse.getContentAsStream)
      // .alsoTo(Sink.onComplete(_ ⇒ page.cleanUp()))
      .withAttributes(StreamAttrs.useProvidedOrBlockingDispatcher)
      .named("htmlunitHttpEntity")

    val entity = HttpEntity(contentType, page.getWebResponse.getContentLength, entityStream)

    HttpResponse(statusCode, headers, entity)
  }
}
