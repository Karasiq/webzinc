package com.karasiq.webzinc.impl.jsoup

import scala.collection.JavaConverters._
import scala.concurrent.Future

import akka.NotUsed
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import com.karasiq.webzinc.{WebClient, WebResourceFetcher}
import com.karasiq.webzinc.config.WebZincConfig
import com.karasiq.webzinc.model.{WebPage, WebResource, WebResources}
import com.karasiq.webzinc.utils.{CSSUtils, StreamUtils, URLUtils}

object JsoupWebResourceFetcher {
  def apply()(implicit config: WebZincConfig, client: WebClient, mat: Materializer): JsoupWebResourceFetcher = {
    new JsoupWebResourceFetcher
  }
}

class JsoupWebResourceFetcher(implicit config: WebZincConfig, client: WebClient, mat: Materializer) extends WebResourceFetcher {
  def getWebPage(url: String): Future[(WebPage, WebResources)] = {
    getByteStream(url)
      .fold(ByteString.empty)(_ ++ _)
      .map { pageBytes ⇒
        val pageHtml = pageBytes.utf8String
        val page = Jsoup.parse(pageHtml, url)
        val resources = embeddedResources(page).concat(cssResources(page))
        (WebPage(url, page.title(), pageHtml), resources)
      }
      .named("jsoupGetWebPage")
      .runWith(Sink.head)
  }

  protected def cssResources(page: Document): Source[WebResource, NotUsed] = {
    def toResource(origin: String, resUrl: String): WebResource = {
      new WebResource {
        val absUrl = URLUtils.resolveUrl(origin, resUrl)
        def dataStream: Source[ByteString, NotUsed] = getByteStream(absUrl)
        def url: String = resUrl
      }
    }

    val staticStyles = page.getElementsByTag("style")
      .asScala
      .map(e ⇒ Source.single((page.location(), e.text())))

    val linkedStyles = page.getElementsByTag("link").asScala
      .filter(_.attr("rel").contains("stylesheet"))
      .map(_.absUrl("href"))
      .filter(_.nonEmpty)
      .map { url ⇒
        getByteStream(url)
          .fold(ByteString.empty)(_ ++ _)
          .map(bs ⇒ (url, bs.utf8String))
          .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      }

    Source((staticStyles ++ linkedStyles).toVector)
      .flatMapConcat(identity)
      .mapConcat { case (origin, stylesheet) ⇒
        val links = CSSUtils.extractCSSResources(stylesheet)
        links.map(toResource(origin, _))
      }
      .log("css-resources")
      .named("jsoupCssResources")
  }

  protected def embeddedResources(page: Document): Source[WebResource, NotUsed] = {
    def srcsOf(es: Elements) = es.eachAttr("src").asScala
    def hrefsOf(es: Elements) = es.eachAttr("href").asScala
    def mediaSrcsOf(es: Elements) = es.asScala.flatMap(e ⇒ e.attr("src") +: srcsOf(e.getElementsByTag("source")))

    def toResource(_url: String): WebResource = new WebResource {
      val absUrl = URLUtils.resolveUrl(page.location(), _url)
      def dataStream: Source[ByteString, NotUsed] = getByteStream(absUrl)
      def url: String = _url
    }

    val images = srcsOf(page.getElementsByTag("img"))
    val videos = mediaSrcsOf(page.getElementsByTag("video"))
    val audios = mediaSrcsOf(page.getElementsByTag("audios"))
    val scripts = srcsOf(page.getElementsByTag("script"))
    val links = hrefsOf(page.getElementsByTag("link"))
    val anchored = hrefsOf(page.getElementsByTag("a")).filter(isSaveableResource)

    val urls = (images ++ videos ++ audios ++ scripts ++ links ++ anchored)
      .filter(url ⇒ url.nonEmpty && !URLUtils.isHashURL(url))
      .toVector

    Source(urls.distinct.map(toResource))
      .log("embedded-resources")
      .named("jsoupEmbeddedResources")
  }
  
  protected def isSaveableResource(url: String) = {
    URLUtils.hasExtension(url, config.saveExtensions)
  }

  protected def getByteStream(url: String) = {
    client.openDataStream(url)
      .via(StreamUtils.applyConfig)
      .named("jsoupByteStream")
  }
}
