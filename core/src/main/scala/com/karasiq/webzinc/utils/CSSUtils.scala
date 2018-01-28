package com.karasiq.webzinc.utils

private[webzinc] object CSSUtils {
  def extractCSSResources(style: String) = {
    val regex = """url\([\"']([^\"']+)[\"']\)""".r
    regex.findAllMatchIn(style).map(m ⇒ m.group(1)).filter(_.nonEmpty).toVector.distinct
  }
}
