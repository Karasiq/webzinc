package com.karasiq.webzinc.utils

object WebZincUtils {
  def isHashURL(url: String): Boolean = {
    url.startsWith("#")
  }

  def isAbsoluteURL(url: String): Boolean = {
    url.startsWith("//") || url.contains("://") || url.startsWith("javascript:")
  }

  def extractCSSResources(style: String) = {
    val regex = """url\([\"']([^\"']+)[\"']\)""".r
    regex.findAllMatchIn(style).map(m ⇒ m.group(1)).toVector.distinct
  }
}
