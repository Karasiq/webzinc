package com.karasiq.webzinc

import scala.concurrent.Future

import akka.stream.Materializer

import com.karasiq.webzinc.config.WebZincConfig
import com.karasiq.webzinc.impl.jsoup.JsoupJSWebResourceInliner
import com.karasiq.webzinc.model.{WebPage, WebResources}

trait WebResourceInliner {
  def inline(page: WebPage, resources: WebResources): Future[WebPage]
}

object WebResourceInliner {
  def apply()(implicit config: WebZincConfig, mat: Materializer): WebResourceInliner = {
    JsoupJSWebResourceInliner()
  }
}