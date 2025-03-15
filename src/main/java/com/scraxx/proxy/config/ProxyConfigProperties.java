package com.scraxx.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "proxy")
@Data
public class ProxyConfigProperties {

    private String proxyProviderUrl = "https://example.com/proxy-list";


    private int proxyFetchIntervalMinutes = 60;


    private int healthCheckIntervalMinutes = 5;
    private int healthCheckTimeoutSeconds = 10;


    private int maxRetriesPerProxy = 3;
    private int maxProxyAttempts = 3;


    private int connectionTimeoutSeconds = 30;
    private int readTimeoutSeconds = 60;
}