package com.scraxx.proxy.service;

import com.scraxx.proxy.config.ProxyConfigProperties;
import com.scraxx.proxy.model.ProxyInfo;
import com.scraxx.proxy.model.ProxyStats;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProxyManagerService {
    private final ProxyConfigProperties config;
    private final RestTemplate restTemplate = new RestTemplate();
    private final AlertService alertService;

    private List<ProxyInfo> proxies = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger currentProxyIndex = new AtomicInteger(0);
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicInteger directRequests = new AtomicInteger(0);

    @PostConstruct
    public void initialize() {
        log.info("Initializing proxy manager service");
        fetchProxies();
        if (!proxies.isEmpty()) {
            checkProxyHealth();
        }
    }

    @Scheduled(fixedDelayString = "${proxy.proxyFetchIntervalMinutes:60}000")
    public void fetchProxies() {
        log.info("Fetching proxy list");
//        try {
//            FetchProxyResponseWrapper  fetchProxyResponseWrapper = restTemplate.exchange(
//                    config.getProxyProviderUrl(),
//                    HttpMethod.GET,
//                    null,
//                    FetchProxyResponseWrapper.class
//            ).getBody();
//
//            if (fetchProxyResponseWrapper != null && fetchProxyResponseWrapper.getData() != null && !fetchProxyResponseWrapper.getData().isEmpty()) {
//                List<ProxyInfo> newProxies = fetchProxyResponseWrapper.getData();
//                log.info("Received {} proxies", newProxies.size());
//                // Preserve health status of existing proxies
//                if (!proxies.isEmpty()) {
//                    for (ProxyInfo newProxy : newProxies) {
//                        proxies.stream()
//                                .filter(p -> p.getIp().equals(newProxy.getIp()) && p.getPort().equals(newProxy.getPort()))
//                                .findFirst()
//                                .ifPresent(existingProxy -> {
//                                    newProxy.setHealthy(existingProxy.isHealthy());
//                                    newProxy.setLastHealthCheck(existingProxy.getLastHealthCheck());
//                                    newProxy.setConsecutiveFailures(existingProxy.getConsecutiveFailures());
//                                });
//                    }
//                }
//                proxies = Collections.synchronizedList(new ArrayList<>(newProxies));
//
//                // Reset the index if it's out of bounds
//                if (currentProxyIndex.get() >= proxies.size()) {
//                    currentProxyIndex.set(0);
//                }
//            } else {
//                log.warn("Received empty proxy list");
//            }
//        } catch (Exception e) {
//            log.error("Failed to fetch proxy list", e);
//        }

        List<ProxyInfo> newProxies = new ArrayList<>();
        for(int i = 0; i < 10; i++){
            ProxyInfo proxyInfo = new ProxyInfo();
            proxyInfo.setIp("p.webshare.io");
            proxyInfo.setPort("1000"+i);
            newProxies.add(proxyInfo);
        }
        proxies = Collections.synchronizedList(new ArrayList<>(newProxies));
    }

    @Scheduled(fixedDelayString = "${proxy.healthCheckIntervalMinutes:5}000")
    public void checkProxyHealth() {
        if (proxies.isEmpty()) {
            log.warn("No proxies available for health check");
            return;
        }

        log.info("Starting health check for {} proxies", proxies.size());

        for (ProxyInfo proxy : proxies) {
            checkSingleProxyHealth(proxy);
        }

        int healthyCount = getHealthyProxies().size();
        log.info("Health check completed. Healthy proxies: {}/{}", healthyCount, proxies.size());

        if (healthyCount == 0 && !proxies.isEmpty()) {
            alertService.alertNoHealthyProxies();
        }
    }

    private void checkSingleProxyHealth(ProxyInfo proxy) {
        String ip = proxy.getIp();
        int port = Integer.parseInt(proxy.getPort());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), config.getHealthCheckTimeoutSeconds() * 1000);
            proxy.setHealthy(true);
            proxy.setConsecutiveFailures(0);
            log.debug("Proxy {}:{} is healthy", ip, port);
        } catch (IOException e) {
            proxy.setConsecutiveFailures(proxy.getConsecutiveFailures() + 1);
            proxy.setHealthy(false);
            log.debug("Proxy {}:{} is unhealthy: {}", ip, port, e.getMessage());
        }

        proxy.setLastHealthCheck(Instant.now());
    }

    public ProxyInfo getNextHealthyProxy() {
        if (proxies.isEmpty()) {
            return null;
        }

        // Find healthy proxies
        List<ProxyInfo> healthyProxies = proxies.stream()
                .filter(ProxyInfo::isHealthy)
                .toList();

        if (healthyProxies.isEmpty()) {
            return null;
        }

        // Get the next index in a thread-safe way
        int index = currentProxyIndex.getAndUpdate(i -> (i + 1) % healthyProxies.size());

        // Ensure index is within bounds
        if (index >= healthyProxies.size()) {
            index = 0;
            currentProxyIndex.set(1); // Set to 1 because we're returning index 0
        }

        return healthyProxies.get(index);
    }

    public void markProxyUnhealthy(ProxyInfo proxy) {
        if (proxy != null) {
            proxy.setHealthy(false);
            proxy.setConsecutiveFailures(proxy.getConsecutiveFailures() + 1);
            log.info("Marked proxy {}:{} as unhealthy", proxy.getIp(), proxy.getPort());
        }
    }

    public ProxyStats getProxyStats() {
        ProxyStats stats = new ProxyStats();
        stats.setTotalProxies(proxies.size());
        stats.setHealthyProxies(getHealthyProxies().size());
        stats.setUnhealthyProxies(proxies.size() - stats.getHealthyProxies());
        stats.setTotalRequests(totalRequests.get());
        stats.setSuccessfulRequests(successfulRequests.get());
        stats.setFailedRequests(failedRequests.get());
        stats.setDirectRequests(directRequests.get());
        return stats;
    }

    public List<ProxyInfo> getHealthyProxies() {
        return proxies.stream().filter(ProxyInfo::isHealthy).toList();
    }

    public void incrementTotalRequests() {
        totalRequests.incrementAndGet();
    }

    public void incrementSuccessfulRequests() {
        successfulRequests.incrementAndGet();
    }

    public void incrementFailedRequests() {
        failedRequests.incrementAndGet();
    }

    public void incrementDirectRequests() {
        directRequests.incrementAndGet();
    }
}