package com.karasiq.webzinc.test

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers, Suite}
import org.scalatest.concurrent.ScalaFutures

abstract class StandardSpec extends TestKit(ActorSystem("test")) with ImplicitSender
  with Suite with Matchers with ScalaFutures with BeforeAndAfterAll with FlatSpecLike {

  implicit val defaultTimeout = Timeout(15 seconds)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  override implicit val patienceConfig: PatienceConfig = {
    PatienceConfig(30 seconds, 300 millis)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
