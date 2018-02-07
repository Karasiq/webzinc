package com.karasiq.webzinc.utils

import java.util.Base64

import akka.util.ByteString

import com.karasiq.webzinc.model.WebPage

private[webzinc] object JSInlinerScript {
  def header(page: WebPage) =
    s"""var webzinc_origin = new URL('${page.url}');
       |var webzinc_resources = {};
       |""".stripMargin

  def resource(url: String, bytes: ByteString) = {
    @inline def base64(data: ByteString) = Base64.getEncoder.encodeToString(data.toArray)
    s"webzinc_resources['$url'] = '${base64(bytes)}';"
  }

  def initScript =
    """
      |document.addEventListener("DOMContentLoaded", function () {
      |    function getResourceData(url) {
      |        var b64Data = webzinc_resources[url];
      |        if (b64Data !== undefined) return atob(b64Data);
      |    }
      |
      |    function dataToUrl(byteCharacters, contentType, sliceSize) {
      |        contentType = contentType || '';
      |        sliceSize = sliceSize || 512;
      |
      |        var byteArrays = [];
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
      |    function toAbsoluteURL(url) {
      |        if (isAbsoluteURL(url)) return url;
      |        else if (url.startsWith('/')) return webzinc_origin.origin + url;
      |        else return webzinc_origin.origin + '/' + url;
      |    }
      |
      |    function isAbsoluteURL(url) {
      |        return url.indexOf('://') != -1 || url.startsWith('//') || url.startsWith('javascript:');
      |    }
      |
      |    function getContentType(url) {
      |        var types = {
      |            png: 'image/png',
      |            jpg: 'image/jpeg',
      |            jpeg: 'image/jpeg',
      |            gif: 'image/gif',
      |            ico: 'image/x-icon',
      |            svg: 'image/svg+xml',
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
      |        var file = url || '';
      |        try { file = new URL(toAbsoluteURL(url)).pathname; } catch (e) { }
      |
      |        var parts = file.split('.');
      |        var extension = parts[parts.length - 1];
      |        return types[extension] || 'application/octet-stream';
      |    }
      |
      |    function processCssLinks(style) {
      |        var regex = /url\(['\"]([^\"']+)[\"']\)/g;
      |        var result = style.replace(regex, function (match, url) {
      |            var data = getResourceData(url);
      |            if (data !== undefined) return 'url("' + dataToUrl(data, getContentType(url)) + '")';
      |            else return match;
      |        });
      |        return result;
      |    }
      |
      |    function processGenericElement(e) {
      |        function replaceAttr(e, attr) {
      |            var url = e.getAttribute(attr);
      |            var data = getResourceData(url);
      |            if (data !== undefined) {
      |                e.setAttribute('orig-' + attr, url);
      |                var contentType = getContentType(url);
      |                if (contentType == 'text/css') data = processCssLinks(data);
      |                e.setAttribute(attr, dataToUrl(data, contentType));
      |            } else if (url && url.indexOf('://') == -1 && !url.startsWith('javascript:') && !url.startsWith('#')) {
      |                e.setAttribute('orig-' + attr, url);
      |                e.setAttribute(attr, toAbsoluteURL(url));
      |            }
      |        }
      |
      |        replaceAttr(e, "href");
      |        replaceAttr(e, "src");
      |    }
      |
      |    function processStyle(style) {
      |        style.textContent = processCssLinks(style.textContent);
      |    }
      |
      |    function foreach(list, f) {
      |        for (var i = 0; i < list.length; i++) f(list[i]);
      |    }
      |
      |    ['a', 'source', 'video', 'audio', 'img', 'script', 'link'].forEach(function (name) {
      |        foreach(document.getElementsByTagName(name), processGenericElement);
      |    });
      |
      |    foreach(document.getElementsByTagName('style'), processStyle);
      |});
    """.stripMargin
}
