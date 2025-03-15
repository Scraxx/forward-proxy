package com.scraxx.proxy.model;

import lombok.Data;

@Data
public class ProxyStats {
    private int totalProxies;
    private int healthyProxies;
    private int unhealthyProxies;
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private int directRequests;
}