package com.karasiq.webzinc.config

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import com.typesafe.config.ConfigFactory

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.webzinc.utils.URLUtils

final case class WebZincConfig(proxy: Option[String], pageSizeLimit: Long, fileSizeLimit: Long, saveExtensions: Set[String], readTimeout: FiniteDuration)

object WebZincConfig {
  lazy val default = loadConfig(ConfigFactory.load())

  def apply(config: Config): WebZincConfig = {
    new WebZincConfig(
      config.optional(_.getString("proxy")).filter(_.nonEmpty),
      config.withDefault(Long.MaxValue, _.getBytes("page-size-limit")),
      config.withDefault(Long.MaxValue, _.getBytes("file-size-limit")),
      config.withDefault(URLUtils.MediaExtensions, _.getStringSet("save-extensions")),
      config.withDefault(10 seconds, _.getFiniteDuration("read-timeout"))
    )
  }

  def apply()(implicit arf: ActorRefFactory): WebZincConfig = arf match {
    case as: ActorSystem ⇒ loadConfig(as.settings.config)
    case ac: ActorContext ⇒ loadConfig(ac.system.settings.config)
    case _ ⇒ throw new IllegalArgumentException(arf.toString)
  }

  private[this] def loadConfig(config: Config) = apply(config.getConfigIfExists("webzinc"))
}
