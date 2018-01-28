package com.karasiq.webzinc.fetcher

import java.net.URL
import java.util.Collections

import scala.concurrent.{ExecutionContext, Future}

import akka.NotUsed
import akka.stream.{ActorAttributes, Attributes, Materializer, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.gargoylesoftware.htmlunit.{HttpMethod, Page, WebResponse, WebResponseData}
import com.gargoylesoftware.htmlunit.html._
import com.gargoylesoftware.htmlunit.util.NameValuePair

import com.karasiq.networkutils.HtmlUnitUtils
import com.karasiq.webzinc.client.WebClient
import com.karasiq.webzinc.model.{WebPage, WebResource}
import com.karasiq.webzinc.utils.{CSSUtils, URLUtils}

object HtmlUnitWebResourceFetcher {
  def apply(js: Boolean = false)(implicit client: WebClient, mat: Materializer, ec: ExecutionContext): HtmlUnitWebResourceFetcher = {
    new HtmlUnitWebResourceFetcher(js)
  }
}

class HtmlUnitWebResourceFetcher(js: Boolean)(implicit client: WebClient, mat: Materializer, ec: ExecutionContext) extends WebResourceFetcher {
  import HtmlUnitUtils._
  protected val webClient = newWebClient(js)

  def getWebPage(url: String) = getPage(url).collect { case page: HtmlPage ⇒
    val pageResource = WebPage(url, page.getTitleText, page.getWebResponse.getContentAsString())
    val resources = embeddedResources(page).concat(cssResources(page))
    (pageResource, resources)
  }

  protected def getPage(url: String): Future[Page] = {
    client.openDataStream(url).runWith(Sink.fold(ByteString.empty)(_ ++ _)).map { pageBytes ⇒
      val pc = webClient.getPageCreator
      val responseData = new WebResponseData(pageBytes.toArray, 200, "OK", Collections.emptyList[NameValuePair]())
      pc.createPage(new WebResponse(responseData, new URL(url), HttpMethod.GET, 1L), webClient.getCurrentWindow)
    }
  }

  protected def embeddedResources(page: HtmlPage) = {
    def toResource(_url: String): WebResource = {
      val fullUrl = page.getFullyQualifiedUrl(_url).toString
      new WebResource {
        def url: String = _url
        def dataStream: Source[ByteString, NotUsed] = {
          Source.single(fullUrl)
            .flatMapConcat(client.openDataStream)
            .withAttributes(Attributes.name("pageResource"))
        }
      }
    }

    val images = page.elementsByTagName[HtmlImage]("img").map(_.getSrcAttribute)
    val videos = page.elementsByTagName[HtmlVideo]("video").flatMap { video ⇒
      video.getAttribute("src") :: video.subElementsByTagName[HtmlSource]("source").map(_.getAttribute("src")).toList
    }
    val audios = page.elementsByTagName[HtmlAudio]("audio").flatMap { audio ⇒
      audio.getAttribute("src") :: audio.subElementsByTagName[HtmlSource]("source").map(_.getAttribute("src")).toList
    }

    val scripts = page.elementsByTagName[HtmlScript]("script").map(_.getSrcAttribute)
    val linked = page.elementsByTagName[HtmlLink]("link").map(_.getHrefAttribute)
    val anchored = page.anchors.map(_.getHrefAttribute).filter(URLUtils.isStdMediaResource)

    val urls = (images ++ videos ++ audios ++ scripts ++ linked ++ anchored)
      .filter(url ⇒ url.nonEmpty && !URLUtils.isHashURL(url))
      .toVector

    Source(urls.distinct.map(toResource))
      .log("embedded-resources")
      .named("embeddedResources")
  }

  protected def cssResources(page: HtmlPage) = {
    def toResources(cssUrl: String, urls: Seq[String]) = {
      val baseUrl = new URL(cssUrl).toURI

      def cssResource(_url: String): WebResource = {
        val fullUrl = if (URLUtils.isAbsoluteURL(_url)) _url else baseUrl.resolve(_url).toString

        new WebResource {
          def url: String = _url
          def dataStream: Source[ByteString, NotUsed] = {
            Source.single(fullUrl)
              .flatMapConcat(client.openDataStream)
              .named("cssResource")
          }
        }
      }

      urls.map(cssResource)
    }

    val staticStyles = page.elementsByTagName[HtmlStyle]("style")
      .map(_.asText())
      .toVector

    val linkedStyles = page.elementsByTagName[HtmlLink]("link")
      .filter(_.getRelAttribute.contains("stylesheet"))
      .map(_.fullUrl(_.getHrefAttribute))
      .toVector

    Source(linkedStyles)
      .flatMapConcat(url ⇒ client.openDataStream(url).fold(ByteString.empty)(_ ++ _).map(bs ⇒ (url, bs.utf8String)))
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .map { case (cssUrl, style) ⇒ toResources(cssUrl, CSSUtils.extractCSSResources(style)) }
      .concat(Source(staticStyles).map(CSSUtils.extractCSSResources).map(toResources(page.getUrl.toString, _)))
      .mapConcat(_.toVector)
      .log("css-resources")
      .named("cssResources")
  }
}
