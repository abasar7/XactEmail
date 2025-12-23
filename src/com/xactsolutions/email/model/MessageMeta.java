package com.xactsolutions.email.model;

import java.time.Instant;
import java.util.Objects;

public class MessageMeta {

    private long id;
    private long userId;
    private String bodyKey;
    private boolean seen;
    private boolean recent;
    private Instant date;


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getBodyKey() {
        return bodyKey;
    }

    public void setBodyKey(String bodyKey) {
        this.bodyKey = bodyKey;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public boolean isRecent() {
        return recent;
    }

    public void setRecent(boolean recent) {
        this.recent = recent;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MessageMeta meta = (MessageMeta) o;
        return id == meta.id && userId == meta.userId && Objects.equals(bodyKey, meta.bodyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, bodyKey);
    }

    @Override
    public String toString() {
        return "MessageMeta{" +
            "id=" + id +
            ", userId=" + userId +
            ", bodyKey='" + bodyKey + '\'' +
            ", seen=" + seen +
            ", recent=" + recent +
            ", date=" + date +
            '}';
    }

}
