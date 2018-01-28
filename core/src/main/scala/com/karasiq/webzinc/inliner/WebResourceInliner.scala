package com.karasiq.webzinc.inliner

import scala.concurrent.Future

import akka.stream.Materializer

import com.karasiq.webzinc.model.{WebPage, WebResources}

trait WebResourceInliner {
  def inline(page: WebPage, resources: WebResources): Future[WebPage]
}

object WebResourceInliner {
  def apply()(implicit mat: Materializer): WebResourceInliner = {
    JSWebResourceInliner()
  }
}