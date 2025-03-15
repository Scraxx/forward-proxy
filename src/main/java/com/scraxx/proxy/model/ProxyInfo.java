package com.scraxx.proxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxyInfo {
    @JsonProperty("_id")
    private String id;
    private String ip;
    private String port;
    private List<String> protocols;
    private String anonymityLevel;
    private String country;
    private String city;
    private Double upTime;
    private Long lastChecked;
    private Double responseTime;
    private Integer upTimeSuccessCount;
    private Integer upTimeTryCount;

    // Fields for proxy health management
    private boolean healthy = true;
    private Instant lastHealthCheck;
    private int consecutiveFailures = 0;
}
