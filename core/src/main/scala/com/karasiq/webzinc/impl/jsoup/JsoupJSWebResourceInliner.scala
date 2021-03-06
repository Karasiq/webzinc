package com.karasiq.webzinc.impl.jsoup

import java.io.IOException

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.ByteString
import com.karasiq.webzinc.WebResourceInliner
import com.karasiq.webzinc.config.WebZincConfig
import com.karasiq.webzinc.model.{WebPage, WebResources}
import com.karasiq.webzinc.utils.{JSInlinerScript, Measure}
import org.jsoup.Jsoup

object JsoupJSWebResourceInliner {
  def apply()(implicit config: WebZincConfig, mat: Materializer, as: ActorSystem): JsoupJSWebResourceInliner = {
    new JsoupJSWebResourceInliner
  }
}

class JsoupJSWebResourceInliner(implicit config: WebZincConfig, mat: Materializer, as: ActorSystem) extends WebResourceInliner with Measure {
  import as.dispatcher
  override protected def log: LoggingAdapter = Logging(as, getClass)

  protected def insertScript(html: String, script: String): String = {
    val parsedPage = Jsoup.parse(html)
    val scripts    = parsedPage.head().getElementsByTag("script")
    if (scripts.isEmpty) {
      parsedPage.head().append("<script>" + script + "</script>")
    } else {
      scripts.first().before("<script>" + script + "</script>")
    }

    // Fix charset
    Option(parsedPage.head().selectFirst("meta[charset]"))
      .getOrElse(parsedPage.head().prependElement("meta"))
      .attr("charset", "utf-8")

    parsedPage.outerHtml()
  }

  def inline(page: WebPage, resources: WebResources) = {
    val resourceBytes = resources
      .filter(r ⇒ !Set("", "/", page.url).contains(r.url))
      .mapAsyncUnordered(config.parallelism) { resource ⇒
        def stream: Source[(String, ByteString), NotUsed] =
          resource.dataStream
            .fold(ByteString.empty)(_ ++ _)
            .map((resource.url, _))

        val future = stream
          .recoverWithRetries(config.retries, { case _: IOException ⇒ stream })
          .recoverWithRetries(1, { case err ⇒
            log.error(err, s"Error loading ${resource.url}")
            Source.empty
          })
          .runWith(Sink.headOption)

        measureFuture(s"Loading resource ${resource.url}", future)
      }
      .statefulMapConcat { () ⇒
        var bytesCount = 0L

        {
          case Some((url, bytes)) if bytesCount < config.pageSizeLimit =>
            bytesCount += bytes.length
            Some(url -> bytes) :: Nil

          case _ =>
            None :: Nil
        }
      }
      .takeWhile(_.nonEmpty)
      .mapConcat(_.toVector)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      // .log("fetched-resources", { case (url, data) ⇒ url + " (" + MemorySize(data.length) + ")" })
      .named("resourceBytes")

    val result = Source
      .single(JSInlinerScript.header(page))
      .concat(resourceBytes.map { case (url, bytes) ⇒ JSInlinerScript.resource(url, bytes) })
      .concat(Source.single(JSInlinerScript.initScript))
      .fold("")(_ + "\n" + _)
      .map(script ⇒ page.copy(html = insertScript(page.html, script)))
      .runWith(Sink.head)

    measureFuture(s"Inlining resources in $page", result)
  }
}
