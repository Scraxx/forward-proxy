package com.scraxx.proxy.service;

import com.scraxx.proxy.config.ProxyConfigProperties;
import com.scraxx.proxy.model.ForwardRequest;
import com.scraxx.proxy.model.ForwardResponse;
import com.scraxx.proxy.model.Header;
import com.scraxx.proxy.model.ProxyInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForwardProxyService {
    private final ProxyManagerService proxyManager;
    private final ProxyConfigProperties config;
    private final AlertService alertService;

    public ForwardResponse forwardRequest(ForwardRequest request) {
        proxyManager.incrementTotalRequests();

        log.info("Forwarding request to URL: {}", request.getUrl());

        // Check if we have any proxies before trying

        ForwardResponse response;

        if (!proxyManager.getHealthyProxies().isEmpty()) {

            response = tryWithProxies(request);


            if (response == null) { // Try direct connection if n proxies fail
                log.warn("All proxy attempts failed, falling back to direct connection");
                alertService.alertFallbackToDirect(request.getUrl());
                response = makeDirectRequest(request);
                if (response != null) {
                    response.setUsedDirectConnection(true);
                    proxyManager.incrementDirectRequests();
                }
            }
        } else {
            // No proxies available, use direct connection
            log.warn("No proxies available, using direct connection");
            alertService.alertFallbackToDirect(request.getUrl());
            response = makeDirectRequest(request);
            if (response != null) {
                response.setUsedDirectConnection(true);
                proxyManager.incrementDirectRequests();
            }
        }

        // If still null, create an error response
        if (response == null) {
            log.error("Failed to forward request to URL: {}", request.getUrl());
            response = new ForwardResponse();
            response.setStatusCode(500);
            response.setBody("Failed to forward request after all attempts");
            proxyManager.incrementFailedRequests();
        } else {
            proxyManager.incrementSuccessfulRequests();
        }

        return response;
    }

    private ForwardResponse tryWithProxies(ForwardRequest request) {
        int proxyAttempts = 0;

        while (proxyAttempts < config.getMaxProxyAttempts()) {
            ProxyInfo proxyInfo = proxyManager.getNextHealthyProxy();

            if (proxyInfo == null) {
                log.warn("No healthy proxies available");
                break;
            }

            String proxyAddress = proxyInfo.getIp() + ":" + proxyInfo.getPort();
            log.info("Attempting request with proxy: {}", proxyAddress);

            ForwardResponse response = tryWithSingleProxy(request, proxyInfo);

            if (response != null) {
                log.info("Request successful with proxy: {}", proxyAddress);
                response.setProxyUsed(proxyAddress);
                return response;
            }

            proxyAttempts++;
            log.warn("Proxy attempt {} failed. Moving to next proxy.", proxyAttempts);
        }

        return null;
    }

    private ForwardResponse tryWithSingleProxy(ForwardRequest request, ProxyInfo proxyInfo) {
        int retries = 0;

        while (retries < config.getMaxRetriesPerProxy()) {
            try {
                // Create a custom RestTemplate with proxy settings
                RestTemplate template = createProxyRestTemplate(proxyInfo);

                // Convert headers
                HttpHeaders headers = convertHeaders(request.getHeaders());

                if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                }

                // Create entity with body

                HttpEntity<String> entity = new HttpEntity<>(request.getBody(), headers);

                // Make the request
                ResponseEntity<String> response = template.exchange(
                        request.getUrl(),
                        HttpMethod.valueOf(request.getMethod().toUpperCase()),
                        entity,
                        String.class
                );

                // Convert the response
                return convertResponse(response, proxyInfo);
            } catch (Exception e) {
                log.warn("Request with proxy {}:{} failed (attempt {}): {}",
                        proxyInfo.getIp(), proxyInfo.getPort(), retries + 1, e.getMessage());
                retries++;

                if (e instanceof ResourceAccessException) {
                    // This is likely a proxy connectivity issue
                    if (retries >= config.getMaxRetriesPerProxy()) {
                        proxyManager.markProxyUnhealthy(proxyInfo);
                    }
                }
            }
        }

        return null;
    }

    private ForwardResponse makeDirectRequest(ForwardRequest request) {
        try {
            // Create a RestTemplate without proxy
            RestTemplate template = createDirectRestTemplate();

            // Convert headers
            HttpHeaders headers = convertHeaders(request.getHeaders());

            if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }


            HttpEntity<String> entity = new HttpEntity<>(request.getBody(), headers);

            // Make the request
            ResponseEntity<String> response = template.exchange(
                    request.getUrl(),
                    HttpMethod.valueOf(request.getMethod().toUpperCase()),
                    entity,
                    String.class
            );

            // Convert the response
            return convertResponse(response, null);
        } catch (HttpStatusCodeException e) {
            // Handle HTTP error responses
            ForwardResponse response = new ForwardResponse();
            response.setStatusCode(e.getStatusCode().value());
            response.setBody(e.getResponseBodyAsString());
            response.setHeaders(convertHeaders(e.getResponseHeaders()));
            response.setUsedDirectConnection(true);
            return response;
        } catch (Exception e) {
            log.error("Direct request failed: {}", e.getMessage());
            return null;
        }
    }

    private RestTemplate createProxyRestTemplate(ProxyInfo proxyInfo) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Set proxy
        InetSocketAddress address = new InetSocketAddress(proxyInfo.getIp(), Integer.parseInt(proxyInfo.getPort()));
        Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
        factory.setProxy(proxy);

        // Set timeouts
        factory.setConnectTimeout(config.getConnectionTimeoutSeconds() * 1000);
        factory.setReadTimeout(config.getReadTimeoutSeconds() * 1000);

        return new RestTemplate(factory);
    }

    private RestTemplate createDirectRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Set timeouts
        factory.setConnectTimeout(config.getConnectionTimeoutSeconds() * 1000);
        factory.setReadTimeout(config.getReadTimeoutSeconds() * 1000);

        return new RestTemplate(factory);
    }

    private HttpHeaders convertHeaders(List<Header> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(header -> httpHeaders.add(header.getName(), header.getValue()));
        }
        return httpHeaders;
    }

    private List<Header> convertHeaders(HttpHeaders httpHeaders) {
        List<Header> headers = new ArrayList<>();
        if (httpHeaders != null) {
            httpHeaders.forEach((name, values) -> values.forEach(value -> {
                Header header = new Header();
                header.setName(name);
                header.setValue(value);
                headers.add(header);
            }));
        }
        return headers;
    }


    private ForwardResponse convertResponse(ResponseEntity<String> responseEntity, ProxyInfo proxyInfo) {
        ForwardResponse response = new ForwardResponse();
        response.setStatusCode(responseEntity.getStatusCode().value());
        response.setBody(responseEntity.getBody());
        response.setHeaders(convertHeaders(responseEntity.getHeaders()));
        response.setUsedDirectConnection(proxyInfo == null);
        if (proxyInfo != null) {
            response.setProxyUsed(proxyInfo.getIp() + ":" + proxyInfo.getPort());
        }
        return response;
    }
}