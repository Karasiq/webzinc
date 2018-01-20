package com.karasiq.webzinc.inliner
import java.util.Base64

import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.jsoup.Jsoup

import com.karasiq.webzinc.model.{WebPage, WebResources}

private[inliner] class JSWebResourceInliner(implicit mat: Materializer) extends WebResourceInliner {
  protected def header(page: WebPage) =
    s"""var webzinc_origin = new URL('${page.url}');
       |var webzinc_resources = {};
       |""".stripMargin

  protected def initScript =
    """
      |document.addEventListener("DOMContentLoaded", function () {
      |    function base64ToUrl(b64Data, contentType, sliceSize) {
      |        contentType = contentType || '';
      |        sliceSize = sliceSize || 512;
      |
      |        var byteCharacters = atob(b64Data);
      |        var byteArrays = [];
      |
      |        for (var offset = 0; offset < byteCharacters.length; offset += sliceSize) {
      |            var slice = byteCharacters.slice(offset, offset + sliceSize);
      |
      |            var byteNumbers = new Array(slice.length);
      |            for (var i = 0; i < slice.length; i++) {
      |                byteNumbers[i] = slice.charCodeAt(i);
      |            }
      |
      |            var byteArray = new Uint8Array(byteNumbers);
      |
      |            byteArrays.push(byteArray);
      |        }
      |        var blob = new Blob(byteArrays, {type: contentType});
      |        return URL.createObjectURL(blob);
      |    }
      |
      |    function getContentType(url) {
      |        var types = {
      |            png: 'image/png',
      |            jpg: 'image/jpeg',
      |            jpeg: 'image/jpeg',
      |            gif: 'image/gif',
      |            pdf: 'application/pdf',
      |            js: 'text/javascript',
      |            css: 'text/css',
      |            html: 'text/html',
      |            htm: 'text/htm',
      |            webm: 'video/webm',
      |            mp4: 'video/mp4',
      |            ogv: 'video/ogv',
      |            mp3: 'audio/mp3',
      |            flac: 'audio/flac',
      |            ogg: 'audio/ogg'
      |        };
      |
      |        for (var type in types) if (url.endsWith('.' + type)) return types[type];
      |    }
      |
      |    function processGenericElement(e) {
      |        function toAbsoluteURL(url) {
      |           if (url.startsWith('/')) return webzinc_origin.origin + url;
      |           else return webzinc_origin.origin + '/' + url;
      |        }
      |
      |        function replaceAttr(e, attr) {
      |            var url = e.getAttribute(attr);
      |            var data = webzinc_resources[url];
      |            if (data !== undefined) {
      |                e.setAttribute('orig-' + attr, url);
      |                e.setAttribute(attr, base64ToUrl(data, getContentType(url)));
      |            } else if (url && !url.contains('://') && !url.startsWith('javascript:') && !url.startsWith('#')) {
      |                e.setAttribute('orig-' + attr, url);
      |                e.setAttribute(attr, toAbsoluteURL(url));
      |            }
      |        }
      |
      |        replaceAttr(e, "href");
      |        replaceAttr(e, "src");
      |    }
      |
      |    function foreach(list, f) {
      |        for (var i = 0; i < list.length; i++) f(list[i]);
      |    }
      |
      |    ['a', 'source', 'video', 'audio', 'img', 'script', 'link'].forEach(function (name) {
      |        foreach(document.getElementsByTagName(name), processGenericElement);
      |    });
      |});
    """.stripMargin

  protected def insertScript(html: String, script: String): String = {
    val parsedPage = Jsoup.parse(html)
    val scripts = parsedPage.head().getElementsByTag("script")
    if (scripts.isEmpty) {
      parsedPage.head().append("<script>" + script + "</script>")
    } else {
      scripts.first().before("<script>" + script + "</script>")
    }

    // Fix charset
    Option(parsedPage.head().selectFirst("meta[charset]"))
      .getOrElse(parsedPage.head().prependElement("meta"))
      .attr("charset", "utf-8")

    parsedPage.outerHtml()
  }

  def inline(page: WebPage, resources: WebResources) = {
    @inline
    def base64(data: ByteString) = Base64.getEncoder.encodeToString(data.toArray)

    val resourceBytes = resources
      .filter(r ⇒ !Set("", "/", page.url).contains(r.url))
      .flatMapMerge(3, { resource ⇒
        resource.dataStream
          .fold(ByteString.empty)(_ ++ _)
          .map((resource.url, _))
          .log("fetched-resources", { case (url, data) ⇒ url + " (" + data.length + " bytes)"})
      })
      .addAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))

    Source.single(header(page))
      .concat(resourceBytes.map { case (url, bytes) ⇒ s"webzinc_resources['$url'] = '${base64(bytes)}';" })
      .concat(Source.single(initScript))
      .fold("")(_ + "\n" + _)
      .map(script ⇒ page.copy(html = insertScript(page.html, script)))
      .runWith(Sink.head)
  }
}
