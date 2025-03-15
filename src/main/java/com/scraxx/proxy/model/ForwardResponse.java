package com.scraxx.proxy.model;

import lombok.Data;

import java.util.List;

@Data
public class ForwardResponse {
    private int statusCode;
    private String body;
    private List<Header> headers;
    private boolean usedDirectConnection;
    private String proxyUsed;
}