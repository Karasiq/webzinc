package com.karasiq.webzinc.utils

import java.net.URI

import scala.util.Try

private[webzinc] object URLUtils {
  val MediaExtensions = Set("jpg", "jpeg", "png", "tif", "tiff", "svg", "pdf", "doc", "rtf", "webm", "mp4", "mp3", "ogg", "ogv", "flac")

  def isHashURL(url: String): Boolean = {
    url.startsWith("#")
  }

  def isAbsoluteURL(url: String): Boolean = {
    url.startsWith("//") || url.contains("://") || url.startsWith("javascript:")
  }

  def resolveUrl(baseUrl: String, relUrl: String) = {
    if (relUrl.contains("://")) {
      relUrl
    } else {
      val uri = Try(new URI(baseUrl))
        .getOrElse(new URI("http://" + baseUrl))
      uri.resolve(relUrl).toString
    }
  }

  def isStdMediaResource(url: String) = {
    val extension = {
      val dotIndex = url.lastIndexOf('.')
      if (dotIndex == -1) "" else url.substring(dotIndex + 1)
    }
    MediaExtensions.contains(extension)
  }
}
