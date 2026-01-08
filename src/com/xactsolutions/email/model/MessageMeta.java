package com.xactsolutions.email.model;

import lombok.Data;

import java.time.Instant;

@Data
public class MessageMeta {

    private long id;
    private long userId;
    private String bodyKey;
    private boolean seen;
    private boolean recent;
    private Instant date;

}
