package com.karasiq.webzinc.utils

import akka.event.LoggingAdapter

import scala.concurrent.{ExecutionContext, Future}

trait Measure {
  protected def log: LoggingAdapter

  def measureFuture[T](name: String, f: Future[T])(implicit ec: ExecutionContext): Future[T] = {
    log.info("{} started", name)
    val start = System.nanoTime()
    f.onComplete(result => log.info("{} finished with {} in {}ms", name, result.getClass.getSimpleName, ((System.nanoTime() - start) * 1e-6).toInt))
    f
  }
}
