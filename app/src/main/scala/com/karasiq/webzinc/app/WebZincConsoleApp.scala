package com.karasiq.webzinc.app

import java.nio.file.{Files, Paths}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.karasiq.webzinc.{WebClient, WebResourceFetcher, WebResourceInliner}
import com.karasiq.webzinc.utils.WebZincUtils

object WebZincConsoleApp extends App {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val client = WebClient()
  val fetcher = WebResourceFetcher()
  val inliner = WebResourceInliner()

  try {
    args.foreach { url ⇒
      println(s"Loading: $url")
      val (page, resources) = Await.result(fetcher.getWebPage(url), Duration.Inf)
      val processedPage = Await.result(inliner.inline(page, resources), Duration.Inf)

      // Files.write(Paths.get(s"${validFileName(page.title)} [${Integer.toHexString(page.url.hashCode)}]_orig.html"), Seq(page.html).asJava)
      Files.write(Paths.get(WebZincUtils.getValidFileName(processedPage)), Seq(processedPage.html).asJava)
    }
  } finally actorSystem.terminate()
}
