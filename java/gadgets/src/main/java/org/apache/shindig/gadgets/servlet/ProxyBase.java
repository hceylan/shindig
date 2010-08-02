/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.gadgets.servlet;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.servlet.HttpUtil;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.common.uri.UriBuilder;
import org.apache.shindig.common.util.Utf8UrlCoder;
import org.apache.shindig.config.ContainerConfig;
import org.apache.shindig.gadgets.GadgetException;
import org.apache.shindig.gadgets.http.HttpRequest;
import org.apache.shindig.gadgets.http.HttpResponse;
import org.apache.shindig.gadgets.uri.UriCommon.Param;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Base class for proxy-based handlers.
 */
public abstract class ProxyBase {
  public static final String URL_PARAM = "url";
  public static final String REFRESH_PARAM = "refresh";
  public static final String IGNORE_CACHE_PARAM = "nocache";
  public static final String GADGET_PARAM = "gadget";
  public static final String CONTAINER_PARAM = "container";
  // Old form container name, retained for legacy compatibility.
  public static final String SYND_PARAM = "synd";

  // Public because of rewriter. Rewriter should be cleaned up.
  public static final String REWRITE_MIME_TYPE_PARAM = Param.REWRITE_MIME_TYPE.getKey();
  public static final String SANITIZE_CONTENT_PARAM = Param.SANITIZE.getKey();

  private static final Logger LOG = Logger.getLogger(ProxyBase.class.getName());
  
  /**
   * Validates the given url.
   *
   * @return A URI representing a validated form of the url.
   * @throws GadgetException If the url is not valid.
   */
  protected Uri validateUrl(Uri urlToValidate) throws GadgetException {
    if (urlToValidate == null) {
      throw new GadgetException(GadgetException.Code.MISSING_PARAMETER, "Missing url param",
          HttpResponse.SC_BAD_REQUEST);
    }
    UriBuilder url = new UriBuilder(urlToValidate);
    if (!"http".equals(url.getScheme()) && !"https".equals(url.getScheme())) {
      throw new GadgetException(GadgetException.Code.INVALID_PARAMETER,
          "Invalid request url scheme in url: " + Utf8UrlCoder.encode(urlToValidate.toString()) +
          "; only \"http\" and \"https\" supported.", HttpResponse.SC_BAD_REQUEST);
    }
    if (url.getPath() == null || url.getPath().length() == 0) {
      url.setPath("/");
    }
    return url.toUri();
  }

  /**
   * Extracts the first parameter from the parameter map with the given name.
   *
   * @param request The request to extract parameters from.
   * @param name The name of the parameter to retrieve.
   * @param defaultValue The default value to use if the parameter is not set.
   * @return The parameter, if found, or defaultValue
   */
  protected String getParameter(HttpServletRequest request, String name, String defaultValue) {
    String ret = request.getParameter(name);
    return ret == null ? defaultValue : ret;
  }

  /**
   * Extracts the container name from the request.
   */
  protected String getContainer(HttpServletRequest request) {
    String container = getParameter(request, CONTAINER_PARAM, null);
    if (container == null) {
      container = getParameter(request, SYND_PARAM, ContainerConfig.DEFAULT_CONTAINER);
    }
    return container;
  }

  /**
   * Sets cache control headers for the response.
   */
  @SuppressWarnings("boxing")
  protected void setResponseHeaders(HttpServletRequest request,
      HttpServletResponse response, HttpResponse results) throws GadgetException {
    int refreshInterval = 0;
    if (results.isStrictNoCache() || "1".equals(request.getParameter(IGNORE_CACHE_PARAM))) {
      refreshInterval = 0;
    } else if (request.getParameter(REFRESH_PARAM) != null) {
      try {
        refreshInterval =  Integer.valueOf(request.getParameter(REFRESH_PARAM));
      } catch (NumberFormatException nfe) {
        throw new GadgetException(GadgetException.Code.INVALID_PARAMETER,
            "refresh parameter is not a number");
      }
    } else {
      refreshInterval = Math.max(60 * 60, (int)(results.getCacheTtl() / 1000L));
    }
    HttpUtil.setCachingHeaders(response, refreshInterval);
    setResponseContentHeaders(response, results);

  }
  
  protected void setResponseContentHeaders(HttpServletResponse response, HttpResponse results) {

    // We're skipping the content disposition header for flash due to an issue with Flash player 10
    // This does make some sites a higher value phishing target, but this can be mitigated by
    // additional referer checks.
    if (!"application/x-shockwave-flash".equalsIgnoreCase(results.getHeader("Content-Type")) &&
        !"application/x-shockwave-flash".equalsIgnoreCase(response.getContentType())) {
      response.setHeader("Content-Disposition", "attachment;filename=p.txt");
    }
    if (results.getHeader("Content-Type") == null) {
      response.setHeader("Content-Type", "application/octet-stream");
    }
  }

  protected void setRequestHeaders(HttpServletRequest servletRequest, HttpRequest req)
      throws GadgetException {
    String xff  = servletRequest.getHeader("X-Forwarded-For");
    String remoteAddr = servletRequest.getRemoteAddr();
    if (!StringUtils.isEmpty(remoteAddr)) {
      if (StringUtils.isEmpty(xff)) {
        xff = servletRequest.getRemoteAddr();
      } else {
        xff = servletRequest.getRemoteAddr() + ", " + xff;
      }
      req.setHeader("X-Forwarded-For", xff);
    }
  }

  /**
   * Processes the given request.
   */
  public final void fetch(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      doFetch(request, response);
    } catch (GadgetException e) {
      outputError(response, e);
    }
  }

  /**
   * Outputs an error message for the request if it fails.
   */
  protected void outputError(HttpServletResponse resp, GadgetException e)
      throws IOException {
    
    int responseCode;
    Level level = Level.FINE;

    // TODO: Recosider if we should be passing on 404 to the user as is.
    switch (e.getCode()) {
      case INTERNAL_SERVER_ERROR:
        responseCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        level = Level.WARNING;
        break;
      default:
        responseCode = HttpServletResponse.SC_BAD_REQUEST;
        break;
    }
    
    LOG.log(level, "Request failed", e);
    resp.sendError(responseCode, e.getMessage());
  }

  abstract protected void doFetch(HttpServletRequest request, HttpServletResponse response)
      throws GadgetException, IOException;

  protected static boolean getIgnoreCache(HttpServletRequest request) {
    String ignoreCache = request.getParameter(IGNORE_CACHE_PARAM);
    if (ignoreCache == null) {
      return false;
    }
    return !"0".equals(ignoreCache);
  }
}
