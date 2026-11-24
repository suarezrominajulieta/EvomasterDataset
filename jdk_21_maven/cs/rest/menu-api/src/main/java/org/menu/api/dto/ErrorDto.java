package org.menu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What every failed request answers with")
public class ErrorDto {

    private int status;
    private String error;
    private String message;
    private String path;

    public ErrorDto() {
    }

    public ErrorDto(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
