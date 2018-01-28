package com.karasiq.webzinc.utils

import scala.concurrent.ExecutionContext

import akka.dispatch.MessageDispatcher
import akka.stream.{ActorAttributes, Attributes}

private[webzinc] object StreamAttrs {
  val useBlockingDispatcher = Attributes(ActorAttributes.IODispatcher)

  def useProvidedOrBlockingDispatcher(implicit ec: ExecutionContext) = ec match {
    case md: MessageDispatcher ⇒
      ActorAttributes.dispatcher(md.id)

    case _ ⇒
      Attributes(ActorAttributes.IODispatcher)
  }
}
