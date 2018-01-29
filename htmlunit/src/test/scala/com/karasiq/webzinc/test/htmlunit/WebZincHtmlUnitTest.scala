package com.karasiq.webzinc.test.htmlunit

import com.karasiq.webzinc.impl.htmlunit.{HtmlUnitWebClient, HtmlUnitWebResourceFetcher}
import com.karasiq.webzinc.test.WebZincSpec

class WebZincHtmlUnitTest extends WebZincSpec {
  implicit val client = HtmlUnitWebClient()
  val fetcher = HtmlUnitWebResourceFetcher()

  testWebClient(client)
  testResourceFetcher(fetcher)
}
