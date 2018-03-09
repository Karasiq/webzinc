package com.karasiq.webzinc.utils

import com.karasiq.webzinc.model.WebPage

object WebZincUtils {
  def getValidFileName(page: WebPage) = {
    def validFileName(str: String) = {
      val forbiddenChars = """[<>:"/\\|?*]""".r
      forbiddenChars.replaceAllIn(str, "_")
    }

    s"${validFileName(page.title).take(200)} [${Integer.toHexString(page.url.hashCode)}].html"
  }

  def getFileName(page: WebPage) = {
    s"${page.title} [${Integer.toHexString(page.url.hashCode)}].html"
  }
}
