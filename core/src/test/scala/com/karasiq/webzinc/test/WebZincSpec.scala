package com.karasiq.webzinc.test

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.stream.scaladsl.Sink

import com.karasiq.webzinc.{WebClient, WebResourceFetcher, WebResourceInliner}

abstract class WebZincSpec extends StandardSpec {
  def testWebClient(wc: WebClient): Unit = {
    "WebClient" should "save page" in {
      val response = wc.doHttpRequest("https://example.com/").futureValue
      val entity = response.entity.toStrict(10 seconds).futureValue

      // println(response.headers)
      // println(entity.data.utf8String)
      response.headers.collect { case `Content-Type`(contentType) ⇒ contentType.mediaType shouldBe MediaTypes.`text/html` }
      // entity.contentLength shouldBe 606
      entity.data.length shouldBe 1270
    }
  }

  def testResourceFetcher(rf: WebResourceFetcher): Unit = {
    "WebResourceFetcher" should "fetch resources" in {
      val (page, resourcesStream) = rf.getWebPage("http://fontawesome.io/icons/").futureValue
      page.url shouldBe "http://fontawesome.io/icons/"
      page.title shouldBe "Font Awesome Icons"
      page.html.length should be >= 20000

      val resources = resourcesStream.runWith(Sink.seq).futureValue.map(_.url)
      resources should contain ("https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js")
      resources should contain ("../fonts/fontawesome-webfont.woff?v=4.7.0")
    }
  }

  def testResourceInliner(rf: WebResourceFetcher, il: WebResourceInliner): Unit = {
    "WebResourceInliner" should "inline resources" in {
      val page = rf.getWebPage("https://en.wikipedia.org/wiki/1856").flatMap(il.inline _ tupled).futureValue
      page.url shouldBe "https://en.wikipedia.org/wiki/1856"
      page.title shouldBe "1856 - Wikipedia"
      page.html.length should be >= 1000000
    }
  }
}
