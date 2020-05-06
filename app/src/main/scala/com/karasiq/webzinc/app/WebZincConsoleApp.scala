package com.karasiq.webzinc.app

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.karasiq.webzinc.WebResourceInliner
import com.karasiq.webzinc.config.WebZincConfig
import com.karasiq.webzinc.impl.akkahttp.AkkaWebClient
import com.karasiq.webzinc.impl.htmlunit.HtmlUnitWebResourceFetcher
import com.karasiq.webzinc.utils.WebZincUtils

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

object WebZincConsoleApp extends App {
  implicit val actorSystem      = ActorSystem()
  implicit val materializer     = ActorMaterializer()
  implicit val config           = WebZincConfig()
  implicit val executionContext = actorSystem.dispatchers.lookup("akka.actor.default-blocking-io-dispatcher")

  val fetcher = HtmlUnitWebResourceFetcher()
  val inliner = WebResourceInliner()

  try {
    val input =
      if (args.nonEmpty) args.iterator
      else Iterator.continually(StdIn.readLine()).takeWhile(_ != null).map(_.trim)

    input.foreach { url ⇒
      val start = System.nanoTime()
      println(s"Loading: $url")
      val (page, resources) = Await.result(fetcher.getWebPage(url), Duration.Inf)
      val processedPage     = Await.result(inliner.inline(page, resources), Duration.Inf)

      // Files.write(Paths.get(s"${validFileName(page.title)} [${Integer.toHexString(page.url.hashCode)}]_orig.html"), Seq(page.html).asJava)
      Files.write(Paths.get(WebZincUtils.getValidFileName(processedPage)), Seq(processedPage.html).asJava)
      println(s"$url loaded in ${(System.nanoTime() - start) * 1e-6} ms")
    }
  } finally actorSystem.terminate()
}
