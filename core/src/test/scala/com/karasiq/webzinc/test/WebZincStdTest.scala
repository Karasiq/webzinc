package com.karasiq.webzinc.test

import scala.language.postfixOps

import com.karasiq.webzinc.{WebClient, WebResourceFetcher, WebResourceInliner}

class WebZincStdTest extends WebZincSpec {
  implicit val client = WebClient()
  val fetcher = WebResourceFetcher()
  val inliner = WebResourceInliner()

  testWebClient(client)
  testResourceFetcher(fetcher)
  testResourceInliner(fetcher, inliner)
}
