package com.scraxx.proxy.model;

import lombok.Data;

import java.util.List;

@Data
public class ForwardRequest {
    private String url;
    private String method;
    private String body;
    private List<Header> headers;
}
