package com.shawn.logging.monitor.handler;

import org.apache.commons.lang.builder.ToStringBuilder;

public final class LogAttribute {

    private String logPath;
    private Long lastModifiedSize;
    private Long currentSize;
    private Long lastModifiedTime;
    private Long totalConsumed = 0L;


    public Long getTotalConsumed() {
        return totalConsumed;
    }

    public void setTotalConsumed(Long totalConsumed) {
        this.totalConsumed = totalConsumed;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public Long getLastModifiedSize() {
        return lastModifiedSize;
    }

    public void setLastModifiedSize(Long lastModifiedSize) {
        this.lastModifiedSize = lastModifiedSize;
    }

    public Long getCurrentSize() {
        return currentSize;
    }

    public void setCurrentSize(Long currentSize) {
        this.currentSize = currentSize;
    }

    public Long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(Long lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public static class Builder {
        private LogAttribute instance = new LogAttribute();

        public Builder logPath(String logPath) {
            instance.setLogPath(logPath);
            return this;
        }

        public Builder lastModifiedSize(Long lastModifiedSize) {
            instance.setLastModifiedSize(lastModifiedSize);
            return this;
        }

        public Builder currentSize(Long currentSize) {
            instance.setCurrentSize(currentSize);
            return this;
        }

        public Builder lastModifiedTime(Long lastModifiedTime) {
            instance.setLastModifiedTime(lastModifiedTime);
            return this;
        }


        public LogAttribute build() {
            return this.instance;
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
