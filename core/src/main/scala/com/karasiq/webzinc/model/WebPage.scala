package com.karasiq.webzinc.model

import akka.util.ByteString

final case class WebPage(url: String, title: String, html: String) extends WebResource.Static {
  def data = ByteString(html)
  override def toString: String = s"WebPage($url, $title, ${html.length} bytes)"
}
