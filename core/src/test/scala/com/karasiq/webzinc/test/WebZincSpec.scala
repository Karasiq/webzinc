package com.karasiq.webzinc.test

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.stream.scaladsl.Sink

import com.karasiq.webzinc.{WebClient, WebResourceFetcher, WebResourceInliner}
import com.karasiq.webzinc.config.WebZincConfig

abstract class WebZincSpec extends StandardSpec {
  implicit val config = WebZincConfig()
  
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
      val (page, resourcesStream) = rf.getWebPage("https://electronics.stackexchange.com/questions/354665/do-pcbs-have-schematics").futureValue
      page.url shouldBe "https://electronics.stackexchange.com/questions/354665/do-pcbs-have-schematics"
      page.title shouldBe "power electronics - Do PCBs have schematics? - Electrical Engineering Stack Exchange"
      page.html.length should be >= 20000

      val resources = resourcesStream.runWith(Sink.seq).futureValue.map(_.url)
      resources should contain ("https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js")
      resources should contain ("img/sprites.svg?v=54431cac5cfa")
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
