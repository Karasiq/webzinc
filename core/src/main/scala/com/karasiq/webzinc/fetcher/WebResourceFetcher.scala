package com.karasiq.webzinc.fetcher

import scala.concurrent.Future

import akka.stream.Materializer

import com.karasiq.webzinc.client.WebClient
import com.karasiq.webzinc.model.{WebPage, WebResources}

trait WebResourceFetcher {
  def getWebPage(url: String): Future[(WebPage, WebResources)]
}

object WebResourceFetcher {
  def apply()(implicit webClient: WebClient, mat: Materializer): WebResourceFetcher = {
    JsoupWebResourceFetcher()
  }
}
