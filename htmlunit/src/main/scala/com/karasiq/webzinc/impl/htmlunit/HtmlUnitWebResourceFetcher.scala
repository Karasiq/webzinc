package com.karasiq.webzinc.impl.htmlunit

import java.net.URL
import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.ByteString
import com.gargoylesoftware.htmlunit
import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.html._
import com.gargoylesoftware.htmlunit.util.NameValuePair
import com.karasiq.common.ThreadLocalFactory
import com.karasiq.networkutils.HtmlUnitUtils
import com.karasiq.webzinc.config.WebZincConfig
import com.karasiq.webzinc.model.{WebPage, WebResource}
import com.karasiq.webzinc.utils._
import com.karasiq.webzinc.{WebClient, WebResourceFetcher}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object HtmlUnitWebResourceFetcher {
  def apply(
      js: Boolean = false,
      externalClient: Option[WebClient] = None
  )(implicit config: WebZincConfig, ec: ExecutionContext, mat: Materializer, as: ActorSystem): HtmlUnitWebResourceFetcher = {
    new HtmlUnitWebResourceFetcher(js, externalClient)
  }
}

class HtmlUnitWebResourceFetcher(js: Boolean, externalClient: Option[WebClient])(
    implicit config: WebZincConfig,
    ec: ExecutionContext,
    mat: Materializer,
    as: ActorSystem
) extends WebResourceFetcher
    with Measure {
  protected val log = Logging(as, getClass)

  import HtmlUnitUtils._

  protected val cache = new Cache
  cache.setMaxSize(200)

  protected val webClient = ThreadLocalFactory(newWebClient(js, cache = cache), (_: htmlunit.WebClient).close())

  def getWebPage(url: String) = getPage(url).collect {
    case htmlPage: HtmlPage ⇒
      log.info("Page loaded: {}", htmlPage)
      val pageResource = WebPage(url, htmlPage.getTitleText, htmlPage.getWebResponse.getContentAsString())
      val resources    = embeddedResources(htmlPage).concat(cssResources(htmlPage))
      (pageResource, resources)
  }

  protected def getPage(url: String): Future[Page] =
    measureFuture(
      s"Loading page $url",
      externalClient match {
        case Some(client) ⇒
          for {
            response ← client.doHttpRequest(url)
            entity   ← response.entity.toStrict(config.readTimeout)
          } yield {
            val pageCreator  = webClient().getPageCreator
            val headers      = response.headers.map(h ⇒ new NameValuePair(h.name(), h.value())).asJava
            val responseData = new WebResponseData(entity.data.toArray, response.status.intValue(), response.status.reason(), headers)
            val webRequest   = new WebRequest(new URL(url), HttpMethod.GET)
            webRequest.setCharset(StandardCharsets.UTF_8)
            val webResponse = new WebResponse(responseData, webRequest, 1000)
            pageCreator.createPage(webResponse, webClient().getCurrentWindow)
          }

        case None ⇒
          Future(webClient().getPage[Page](url))
      }
    )

  protected def embeddedResources(page: HtmlPage) = {
    def toResource(_url: String): WebResource = {
      val fullUrl = page.getFullyQualifiedUrl(_url).toString
      new WebResource {
        def url: String                             = _url
        def dataStream: Source[ByteString, NotUsed] = getByteStream(fullUrl)
      }
    }

    val images = page.elementsByTagName[HtmlImage]("img").map(_.getSrcAttribute)
    val videos = page.elementsByTagName[HtmlVideo]("video").flatMap { video ⇒
      video.getAttribute("src") :: video.subElementsByTagName[HtmlSource]("source").map(_.getAttribute("src")).toList
    }
    val audios = page.elementsByTagName[HtmlAudio]("audio").flatMap { audio ⇒
      audio.getAttribute("src") :: audio.subElementsByTagName[HtmlSource]("source").map(_.getAttribute("src")).toList
    }

    val scripts  = page.elementsByTagName[HtmlScript]("script").map(_.getSrcAttribute)
    val anchored = page.anchors.map(_.getHrefAttribute).filter(isSaveableResource)
    val linked = page
      .elementsByTagName[HtmlLink]("link")
      .filter { link =>
        val rel      = link.getRelAttribute
        val relevant = Seq("stylesheet", "icon")
        relevant.exists(rel.contains)
      }
      .map(_.getHrefAttribute)

    val urls = (images ++ videos ++ audios ++ scripts ++ linked ++ anchored)
      .filter(url ⇒ url.nonEmpty && !URLUtils.isHashURL(url))
      .toVector

    Source(urls.distinct.map(toResource))
    //.log("embedded-resources")
      .named("htmlunitEmbeddedResources")
  }

  protected def cssResources(page: HtmlPage) = {
    def toResources(cssUrl: String, urls: Seq[String]) = {
      val baseUrl = new URL(cssUrl).toURI

      def cssResource(_url: String): WebResource = {
        val fullUrl = if (URLUtils.isAbsoluteURL(_url)) _url else baseUrl.resolve(_url).toString

        new WebResource {
          def url: String                             = _url
          def dataStream: Source[ByteString, NotUsed] = getByteStream(fullUrl)
        }
      }

      urls.map(cssResource)
    }

    val staticStyles = page
      .elementsByTagName[HtmlStyle]("style")
      .map(_.asText())
      .toVector

    val linkedStyles = page
      .elementsByTagName[HtmlLink]("link")
      .filter(_.getRelAttribute.contains("stylesheet"))
      .map(_.fullUrl(_.getHrefAttribute))
      .toVector

    Source(linkedStyles)
      .flatMapConcat(url ⇒ getByteStream(url).fold(ByteString.empty)(_ ++ _).map(bs ⇒ (url, bs.utf8String)))
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .map { case (cssUrl, style) ⇒ toResources(cssUrl, CSSUtils.extractCSSResources(style)) }
      .concat(Source(staticStyles).map(CSSUtils.extractCSSResources).map(toResources(page.getUrl.toString, _)))
      .mapConcat(_.toVector)
      .log("css-resources")
      .named("htmlunitCssResources")
  }

  protected def isSaveableResource(url: String) = {
    URLUtils.hasExtension(url, config.saveExtensions)
  }

  protected def getByteStream(url: String) = {
    val stream = externalClient match {
      case Some(client) ⇒
        client.openDataStream(url)

      case None ⇒
        Source.single(url).async.flatMapConcat { url ⇒
          StreamConverters
            .fromInputStream { () ⇒
              val page = webClient().getPage[Page](url)
              page.getWebResponse.getContentAsStream
            }
            .withAttributes(StreamAttrs.useProvidedOrBlockingDispatcher)
        }
    }

    stream
      .via(StreamUtils.applyConfig)
      .named("htmlunitByteStream")
  }
}
