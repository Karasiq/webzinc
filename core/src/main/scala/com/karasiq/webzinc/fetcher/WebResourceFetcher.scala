package com.karasiq.webzinc.fetcher

import scala.concurrent.{ExecutionContext, Future}

import com.karasiq.webzinc.model.{WebPage, WebResources}

trait WebResourceFetcher {
  def getWebPage(url: String): Future[(WebPage, WebResources)]
}

object WebResourceFetcher {
  def apply()(implicit ec: ExecutionContext): WebResourceFetcher = {
    new HtmlUnitWebResourceFetcher()
  }
}
