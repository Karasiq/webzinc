package com.karasiq.webzinc.impl.jsoup

import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.jsoup.Jsoup

import com.karasiq.webzinc.WebResourceInliner
import com.karasiq.webzinc.model.{WebPage, WebResources}
import com.karasiq.webzinc.utils.JSInlinerScript

object JsoupJSWebResourceInliner {
  def apply()(implicit mat: Materializer): JsoupJSWebResourceInliner = {
    new JsoupJSWebResourceInliner
  }
}

class JsoupJSWebResourceInliner(implicit mat: Materializer) extends WebResourceInliner {
  protected def insertScript(html: String, script: String): String = {
    val parsedPage = Jsoup.parse(html)
    val scripts = parsedPage.head().getElementsByTag("script")
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
      .flatMapMerge(2, { resource ⇒
        def stream = resource.dataStream
          .fold(ByteString.empty)(_ ++ _)
          .map((resource.url, _))

        stream
          .recoverWithRetries(3, { case _ ⇒ stream })
          .recoverWithRetries(1, { case _ ⇒ Source.empty })
      })
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .log("fetched-resources", { case (url, data) ⇒ url + " (" + data.length + " bytes)"})
      .named("resourceBytes")

    Source.single(JSInlinerScript.header(page))
      .concat(resourceBytes.map { case (url, bytes) ⇒ JSInlinerScript.resource(url, bytes) })
      .concat(Source.single(JSInlinerScript.initScript))
      .fold("")(_ + "\n" + _)
      .map(script ⇒ page.copy(html = insertScript(page.html, script)))
      .runWith(Sink.head)
  }
}
