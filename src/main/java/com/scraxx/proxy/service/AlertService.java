package com.scraxx.proxy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AlertService {

    /**
     * Alert when no healthy proxies are available
     * This is a placeholder method to be implemented by the user
     */
    public void alertNoHealthyProxies() {
        log.warn("ALERT: No healthy proxies available!");
        // TODO: Implement your custom alerting logic here
    }

    /**
     * Alert when falling back to direct connection
     * This is a placeholder method to be implemented by the user
     */
    public void alertFallbackToDirect(String url) {
        log.warn("ALERT: Falling back to direct connection for URL: {}", url);
        // TODO: Implement your custom alerting logic here
    }
}