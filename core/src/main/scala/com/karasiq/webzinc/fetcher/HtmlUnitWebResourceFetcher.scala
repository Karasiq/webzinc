package com.karasiq.webzinc.fetcher

import scala.concurrent.{ExecutionContext, Future}

import akka.NotUsed
import akka.stream.{ActorAttributes, Attributes}
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html._

import com.karasiq.networkutils.HtmlUnitUtils
import com.karasiq.networkutils.url._
import com.karasiq.webzinc.model.{WebPage, WebResource}

private[fetcher] class HtmlUnitWebResourceFetcher(implicit ec: ExecutionContext) extends WebResourceFetcher {
  import HtmlUnitUtils._
  val webClient = newWebClient(js = false)

  def getWebPage(url: String) = Future {
    def needToSave(url: String) = {
      val extensionsToSave = Set("jpg", "jpeg", "png", "tif", "tiff", "pdf", "doc", "rtf", "webm", "mp4")
      val extension = {
        val dotIndex = url.lastIndexOf('.')
        if (dotIndex == -1) "" else url.substring(dotIndex + 1)
      }
      extensionsToSave.contains(extension)
    }

    webClient.withGetHtmlPage(url) { page ⇒
      def toResource(_url: String) = {
        val fullUrl = page.getFullyQualifiedUrl(_url)
        new WebResource {
          def url: String = _url
          def dataStream: Source[ByteString, NotUsed] = {
            Source.single(NotUsed).flatMapConcat { _ ⇒
              webClient.withGetPage(fullUrl) { p: Page ⇒
                StreamConverters.fromInputStream(() ⇒ p.getWebResponse.getContentAsStream)
              }
            } withAttributes(Attributes.name("htmlUnitResource") and ActorAttributes.IODispatcher)
          }
        }
      }

      val pageResource = WebPage(url, page.getTitleText, page.getWebResponse.getContentAsString())
      val resources = {
        val images = page.elementsByTagName[HtmlImage]("img").map(_.getSrcAttribute)
        val videos = page.elementsByTagName[HtmlVideo]("video").flatMap { video ⇒
          video.getAttribute("src") :: video.subElementsByTagName[HtmlSource]("source").map(_.getAttribute("src")).toList
        }
        val scripts = page.elementsByTagName[HtmlScript]("script").map(_.getSrcAttribute)
        val linked = page.elementsByTagName[HtmlLink]("link").map(_.getHrefAttribute)
        val anchored = page.anchors.map(_.getHrefAttribute).filter(needToSave)
        (images ++ videos ++ scripts ++ linked ++ anchored).filter(_.nonEmpty).toVector.distinct
      }
      (pageResource, Source(resources.map(toResource)))
    }
  }
}
