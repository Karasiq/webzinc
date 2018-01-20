package com.karasiq.webzinc.fetcher

import java.net.URL

import scala.concurrent.{ExecutionContext, Future}

import akka.NotUsed
import akka.dispatch.MessageDispatcher
import akka.stream.{ActorAttributes, Attributes, Supervision}
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.html._

import com.karasiq.networkutils.HtmlUnitUtils
import com.karasiq.networkutils.url._
import com.karasiq.webzinc.model.{WebPage, WebResource}

private[fetcher] class HtmlUnitWebResourceFetcher(implicit ec: ExecutionContext) extends WebResourceFetcher {
  import HtmlUnitUtils._
  protected val webClient = newWebClient(js = false)

  protected def toByteStream(fullUrl: String) = {
    webClient.withGetPage(fullUrl) { p: Page ⇒
      StreamConverters.fromInputStream(() ⇒ p.getWebResponse.getContentAsStream)
    }
  }

  protected def extractStylesheetResources(style: String) = {
    val regex = """url\([\"']([^\"']+)[\"']\)""".r
    regex.findAllMatchIn(style).map(m ⇒ m.group(1)).toVector.distinct
  }

  protected def needToSave(url: String) = {
    val extensionsToSave = Set("jpg", "jpeg", "png", "tif", "tiff", "svg", "pdf", "doc", "rtf", "webm", "mp4", "mp3", "ogg", "ogv", "flac")
    val extension = {
      val dotIndex = url.lastIndexOf('.')
      if (dotIndex == -1) "" else url.substring(dotIndex + 1)
    }
    extensionsToSave.contains(extension)
  }

  protected val blockingAttributes = ec match {
    case md: MessageDispatcher ⇒
      ActorAttributes.dispatcher(md.id)

    case _ ⇒
      Attributes(ActorAttributes.IODispatcher)
  }

  def getWebPage(url: String) = Future {
    webClient.withGetHtmlPage(url) { page ⇒
      def toResource(_url: String): WebResource = {
        val fullUrl = page.getFullyQualifiedUrl(_url).toString
        new WebResource {
          def url: String = _url
          def dataStream: Source[ByteString, NotUsed] = {
            Source.single(fullUrl)
              .flatMapConcat(toByteStream)
              .withAttributes(Attributes.name("pageResource") and blockingAttributes)
          }
        }
      }

      def cssResources(cssUrl: String, urls: Seq[String]) = {
        val baseUrl = new URL(cssUrl).toURI
        // val parentPath = baseUrl.getPath.split('/').filter(_.nonEmpty).dropRight(1)

        def cssResource(_url: String): WebResource = {
          /* @tailrec
          def normalizePath(pageNodes: Seq[String], urlNodes: Seq[String]): Seq[String] = urlNodes match {
            case Nil ⇒
              pageNodes

            case ".." +: rest ⇒
              normalizePath(pageNodes.dropRight(1), rest)

            case "." +: rest ⇒
              normalizePath(pageNodes, rest)

            case path +: rest ⇒
              normalizePath(pageNodes :+ path, rest)
          } */

          val fullUrl = if (_url.contains("://")) _url else baseUrl.resolve(_url).toString

          new WebResource {
            def url: String = _url
            def dataStream: Source[ByteString, NotUsed] = {
              Source.single(fullUrl)
                .log("css-resources")
                .flatMapConcat(toByteStream)
                .withAttributes(Attributes.name("cssResource") and blockingAttributes)
            }
          }
        }

        urls.map(cssResource)
      }

      val pageResource = WebPage(url, page.getTitleText, page.getWebResponse.getContentAsString())

      val embeddedResources = {
        val images = page.elementsByTagName[HtmlImage]("img").map(_.getSrcAttribute)
        val videos = page.elementsByTagName[HtmlVideo]("video").flatMap { video ⇒
          video.getAttribute("src") :: video.subElementsByTagName[HtmlSource]("source").map(_.getAttribute("src")).toList
        }
        val audios = page.elementsByTagName[HtmlAudio]("audio").flatMap { audio ⇒
          audio.getAttribute("src") :: audio.subElementsByTagName[HtmlSource]("source").map(_.getAttribute("src")).toList
        }
        val scripts = page.elementsByTagName[HtmlScript]("script").map(_.getSrcAttribute)
        val linked = page.elementsByTagName[HtmlLink]("link").map(_.getHrefAttribute)
        val anchored = page.anchors.map(_.getHrefAttribute).filter(needToSave)
        (images ++ videos ++ audios ++ scripts ++ linked ++ anchored).filter(_.nonEmpty).toVector.distinct
      }

      val stylesheetResources = {
        val staticStyles = page.elementsByTagName[HtmlStyle]("style")
          .map(_.asText())
          .toVector
        
        val linkedStyles = page.elementsByTagName[HtmlLink]("link")
          .filter(_.getRelAttribute.contains("stylesheet"))
          .map(_.fullUrl(_.getHrefAttribute))
          .toVector

        Source(linkedStyles)
          .flatMapConcat(url ⇒ toByteStream(url).fold(ByteString.empty)(_ ++ _).map(bs ⇒ (url, bs.utf8String)))
          .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider) and blockingAttributes)
          .map { case (cssUrl, style) ⇒ cssResources(cssUrl, extractStylesheetResources(style)) }
          .concat(Source(staticStyles).map(extractStylesheetResources).map(cssResources(page.getUrl.toString, _)))
          .mapConcat(_.toVector)
          .named("stylesheetResources")
      }

      val resources = Source(embeddedResources.map(toResource))
        .concat(stylesheetResources)
        .named("pageResources")

      (pageResource, resources)
    }
  }
}
