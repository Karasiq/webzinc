package com.karasiq.webzinc

import scala.concurrent.Future

import akka.stream.Materializer

import com.karasiq.webzinc.config.WebZincConfig
import com.karasiq.webzinc.impl.jsoup.JsoupWebResourceFetcher
import com.karasiq.webzinc.model.{WebPage, WebResources}

trait WebResourceFetcher {
  def getWebPage(url: String): Future[(WebPage, WebResources)]
}

object WebResourceFetcher {
  def apply()(implicit config: WebZincConfig, client: WebClient, mat: Materializer): WebResourceFetcher = {
    JsoupWebResourceFetcher()
  }
}
