/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package testsupport;

import com.github.tomakehurst.wiremock.http.GenericHttpUriRequest;
import wiremock.org.apache.http.HttpEntity;
import wiremock.org.apache.http.HttpHost;
import wiremock.org.apache.http.HttpResponse;
import wiremock.org.apache.http.auth.AuthScope;
import wiremock.org.apache.http.auth.UsernamePasswordCredentials;
import wiremock.org.apache.http.client.AuthCache;
import wiremock.org.apache.http.client.CredentialsProvider;
import wiremock.org.apache.http.client.HttpClient;
import wiremock.org.apache.http.client.methods.*;
import wiremock.org.apache.http.client.protocol.HttpClientContext;
import wiremock.org.apache.http.entity.ContentType;
import wiremock.org.apache.http.entity.InputStreamEntity;
import wiremock.org.apache.http.entity.StringEntity;
import wiremock.org.apache.http.impl.auth.BasicScheme;
import wiremock.org.apache.http.impl.client.BasicAuthCache;
import wiremock.org.apache.http.impl.client.BasicCredentialsProvider;
import wiremock.org.apache.http.impl.client.HttpClientBuilder;
import wiremock.org.apache.http.impl.client.HttpClients;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked;
import static com.github.tomakehurst.wiremock.http.MimeType.JSON;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.net.HttpURLConnection.*;
import static wiremock.org.apache.http.entity.ContentType.APPLICATION_JSON;

public class WireMockTestClient {

    private static final String LOCAL_WIREMOCK_ROOT = "http://%s:%d%s";
    private static final String LOCAL_WIREMOCK_NEW_RESPONSE_URL = "http://%s:%d/__admin/mappings/new";
    private static final String LOCAL_WIREMOCK_REMOVE_RESPONSE_URL = "http://%s:%d/__admin/mappings/remove";
    private static final String LOCAL_WIREMOCK_EDIT_RESPONSE_URL = "http://%s:%d/__admin/mappings/edit";
    private static final String LOCAL_WIREMOCK_RESET_URL = "http://%s:%d/__admin/reset";
    private static final String LOCAL_WIREMOCK_RESET_DEFAULT_MAPPINS_URL = "http://%s:%d/__admin/mappings/reset";

    private int port;
    private String address;

    public WireMockTestClient(int port, String address) {
        this.port = port;
        this.address = address;
    }

    public WireMockTestClient(int port) {
        this(port, "localhost");
    }

    public WireMockTestClient() {
        this(8080);
    }

    private String mockServiceUrlFor(String path) {
        return String.format(LOCAL_WIREMOCK_ROOT, address, port, path);
    }

    private String newMappingUrl() {
        return String.format(LOCAL_WIREMOCK_NEW_RESPONSE_URL, address, port);
    }

    private String editMappingUrl() {
        return String.format(LOCAL_WIREMOCK_EDIT_RESPONSE_URL, address, port);
    }
    private String removeMappingUrl() {
        return String.format(LOCAL_WIREMOCK_REMOVE_RESPONSE_URL, address, port);
    }
    private String resetUrl() {
        return String.format(LOCAL_WIREMOCK_RESET_URL, address, port);
    }

    private String resetDefaultMappingsUrl() {
        return String.format(LOCAL_WIREMOCK_RESET_DEFAULT_MAPPINS_URL, address, port);
    }

    public WireMockResponse get(String url, TestHttpHeader... headers) {
        String actualUrl = URI.create(url).isAbsolute() ? url : mockServiceUrlFor(url);
        HttpUriRequest httpRequest = new HttpGet(actualUrl);
        return executeMethodAndConvertExceptions(httpRequest, headers);
    }

    public WireMockResponse getViaProxy(String url) {
        return getViaProxy(url, port);
    }

    public WireMockResponse getViaProxy(String url, int proxyPort) {
        URI targetUri = URI.create(url);
        HttpHost proxy = new HttpHost(address, proxyPort, targetUri.getScheme());
        HttpClient httpClientUsingProxy = HttpClientBuilder.create()
            .disableAuthCaching()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableRedirectHandling()
            .setProxy(proxy)
            .build();

        try {
            HttpHost target = new HttpHost(targetUri.getHost(), targetUri.getPort(), targetUri.getScheme());
            HttpGet req = new HttpGet(targetUri.getPath() +
                (isNullOrEmpty(targetUri.getQuery()) ? "" : "?" + targetUri.getQuery()));
            req.removeHeaders("Host");

            System.out.println("executing request to " + targetUri + "(" + target + ") via " + proxy);
            HttpResponse httpResponse = httpClientUsingProxy.execute(target, req);
            return new WireMockResponse(httpResponse);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public WireMockResponse put(String url, TestHttpHeader... headers) {
        HttpUriRequest httpRequest = new HttpPut(mockServiceUrlFor(url));
        return executeMethodAndConvertExceptions(httpRequest, headers);
    }

    public WireMockResponse putWithBody(String url, String body, String contentType, TestHttpHeader... headers) {
        HttpPut httpPut = new HttpPut(mockServiceUrlFor(url));
        return requestWithBody(httpPut, body, contentType, headers);
    }

    public WireMockResponse patchWithBody(String url, String body, String contentType, TestHttpHeader... headers) {
        HttpPatch httpPatch = new HttpPatch(mockServiceUrlFor(url));
        return requestWithBody(httpPatch, body, contentType, headers);
    }

    private WireMockResponse requestWithBody(
        HttpEntityEnclosingRequestBase request, String body, String contentType, TestHttpHeader... headers) {
        request.setEntity(new StringEntity(body, ContentType.create(contentType, "utf-8")));
        return executeMethodAndConvertExceptions(request, headers);
    }

    public WireMockResponse postWithBody(String url, String body, String bodyMimeType, String bodyEncoding) {
        return post(url, new StringEntity(body, ContentType.create(bodyMimeType, bodyEncoding)));
    }

    public WireMockResponse postWithChunkedBody(String url, byte[] body) {
        return post(url, new InputStreamEntity(new ByteArrayInputStream(body), -1));
    }

    public WireMockResponse post(String url, HttpEntity entity, TestHttpHeader... headers) {
        HttpPost httpPost = new HttpPost(mockServiceUrlFor(url));
        httpPost.setEntity(entity);
        return executeMethodAndConvertExceptions(httpPost, headers);
    }

    public WireMockResponse postJson(String url, String body, TestHttpHeader... headers) {
        HttpPost httpPost = new HttpPost(mockServiceUrlFor(url));
        httpPost.setEntity(new StringEntity(body, APPLICATION_JSON));
        return executeMethodAndConvertExceptions(httpPost, headers);
    }

    public WireMockResponse patchWithBody(String url, String body, String bodyMimeType, String bodyEncoding) {
        return patch(url, new StringEntity(body, ContentType.create(bodyMimeType, bodyEncoding)));
    }

    public WireMockResponse patch(String url, HttpEntity entity) {
        HttpPatch httpPatch = new HttpPatch(mockServiceUrlFor(url));
        httpPatch.setEntity(entity);
        return executeMethodAndConvertExceptions(httpPatch);
    }

    public WireMockResponse delete(String url) {
        HttpDelete httpDelete = new HttpDelete(mockServiceUrlFor(url));
        return executeMethodAndConvertExceptions(httpDelete);
    }

    public WireMockResponse options(String url, TestHttpHeader... headers) {
        HttpOptions httpOptions = new HttpOptions(mockServiceUrlFor(url));
        return executeMethodAndConvertExceptions(httpOptions, headers);
    }

    public void addResponse(String responseSpecJson) {
        int status = postJsonAndReturnStatus(newMappingUrl(), responseSpecJson);
        if (status != HTTP_CREATED) {
            throw new RuntimeException("Returned status code was " + status);
        }
    }

    public void editMapping(String mappingSpecJson) {
        int status = postJsonAndReturnStatus(editMappingUrl(), mappingSpecJson);
        if (status != HTTP_NO_CONTENT) {
            throw new RuntimeException("Returned status code was " + status);
        }
    }

    public void resetMappings() {
        int status = postEmptyBodyAndReturnStatus(resetUrl());
        if (status != HTTP_OK) {
            throw new RuntimeException("Returned status code was " + status);
        }
    }

    public void resetDefaultMappings() {
        int status = postEmptyBodyAndReturnStatus(resetDefaultMappingsUrl());
        if (status != HTTP_OK) {
            throw new RuntimeException("Returned status code was " + status);
        }
    }

    private int postJsonAndReturnStatus(String url, String json) {
        HttpPost post = new HttpPost(url);
        try {
            if (json != null) {
                post.setEntity(new StringEntity(json, ContentType.create(JSON.toString(), "utf-8")));
            }
            HttpResponse httpResponse = httpClient().execute(post);
            return httpResponse.getStatusLine().getStatusCode();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int postEmptyBodyAndReturnStatus(String url) {
        return postJsonAndReturnStatus(url, null);
    }

    private WireMockResponse executeMethodAndConvertExceptions(HttpUriRequest httpRequest, TestHttpHeader... headers) {
        try {
            for (TestHttpHeader header : headers) {
                httpRequest.addHeader(header.getName(), header.getValue());
            }
            HttpResponse httpResponse = httpClient().execute(httpRequest);
            return new WireMockResponse(httpResponse);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public WireMockResponse getWithPreemptiveCredentials(String url, int port, String username, String password) {
        HttpHost target = new HttpHost("localhost", port);
        HttpClient httpClient = httpClientWithPreemptiveAuth(target, username, password);

        AuthCache authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(target, basicAuth);
        HttpClientContext localContext = HttpClientContext.create();
        localContext.setAuthCache(authCache);

        try {
            HttpGet httpget = new HttpGet(url);
            HttpResponse response = httpClient.execute(target, httpget, localContext);
            return new WireMockResponse(response);
        } catch (IOException e) {
            return throwUnchecked(e, WireMockResponse.class);
        }
    }

    public WireMockResponse request(final String methodName, String url, TestHttpHeader... headers) {
        HttpUriRequest httpRequest = new GenericHttpUriRequest(methodName, mockServiceUrlFor(url));
        return executeMethodAndConvertExceptions(httpRequest, headers);
    }

    private static HttpClient httpClient() {
        return HttpClientBuilder.create()
            .disableAuthCaching()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableRedirectHandling()
            .disableContentCompression()
            .build();
    }

    private static HttpClient httpClientWithPreemptiveAuth(HttpHost target, String username, String password) {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
            new AuthScope(target),
            new UsernamePasswordCredentials(username, password));

        return HttpClients.custom()
            .setDefaultCredentialsProvider(credsProvider)
            .build();
    }
}
