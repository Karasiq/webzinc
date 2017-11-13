package com.karasiq.webzinc.app

import java.nio.file.{Files, Paths}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.karasiq.webzinc.fetcher.WebResourceFetcher
import com.karasiq.webzinc.inliner.WebResourceInliner

object WebZincConsoleApp extends App {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val fetcher = WebResourceFetcher()
  val inliner = WebResourceInliner()

  args.foreach { url ⇒
    println(s"Loading: $url")
    val (page, resources) = Await.result(fetcher.getWebPage(url), Duration.Inf)
    val processedPage = Await.result(inliner.inline(page, resources), Duration.Inf)
    
    def validFileName(str: String) = {
      val forbiddenChars = """[<>:"/\\|?*]""".r
      forbiddenChars.replaceAllIn(str, "_")
    }

    // Files.write(Paths.get(s"${validFileName(page.title)} [${Integer.toHexString(page.url.hashCode)}]_orig.html"), Seq(page.html).asJava)
    Files.write(Paths.get(s"${validFileName(page.title)} [${Integer.toHexString(page.url.hashCode)}].html"), Seq(processedPage.html).asJava)
  }

  actorSystem.terminate()
}
