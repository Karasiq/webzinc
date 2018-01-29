package com.karasiq.webzinc.test

import scala.language.postfixOps

import com.karasiq.webzinc.{WebClient, WebResourceFetcher}

class WebZincStdTest extends WebZincSpec {
  implicit val client = WebClient()
  val fetcher = WebResourceFetcher()

  testWebClient(client)
  testResourceFetcher(fetcher)
}
