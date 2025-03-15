package com.scraxx.proxy.controller;

import com.scraxx.proxy.model.ForwardRequest;
import com.scraxx.proxy.model.ForwardResponse;
import com.scraxx.proxy.model.ProxyInfo;
import com.scraxx.proxy.model.ProxyStats;
import com.scraxx.proxy.service.ForwardProxyService;
import com.scraxx.proxy.service.ProxyManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proxy")
@RequiredArgsConstructor
@Slf4j
public class ProxyController {

    private final ForwardProxyService proxyService;
    private final ProxyManagerService proxyManager;

    @PostMapping("/forward")
    public ResponseEntity<ForwardResponse> forwardRequest(@RequestBody ForwardRequest request) {
        log.info("Received forward request for URL: {}", request.getUrl());
        ForwardResponse response = proxyService.forwardRequest(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/healthyProxies")
    public ResponseEntity<List<ProxyInfo>> health() {
        return ResponseEntity.ok().body(proxyManager.getHealthyProxies());
    }

    @GetMapping("/stats")
    public ResponseEntity<ProxyStats> getStats() {
        return ResponseEntity.ok(proxyManager.getProxyStats());
    }

    @PostMapping("/refresh-proxies")
    public ResponseEntity<String> refreshProxies() {
        proxyManager.fetchProxies();
        return ResponseEntity.ok("Proxy refresh initiated");
    }

    @PostMapping("/check-health")
    public ResponseEntity<String> checkHealth() {
        proxyManager.checkProxyHealth();
        return ResponseEntity.ok("Health check initiated");
    }
}