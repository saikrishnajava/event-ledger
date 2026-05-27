package com.eventledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private Instant timestamp;
    private Map<String, String> details;

    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, String> getDetails() { return details; }
    public void setDetails(Map<String, String> details) { this.details = details; }
}
