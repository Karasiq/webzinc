package com.karasiq.webzinc

import akka.NotUsed
import akka.stream.scaladsl.Source

package object model {
  type WebResources = Source[WebResource, NotUsed]
}
