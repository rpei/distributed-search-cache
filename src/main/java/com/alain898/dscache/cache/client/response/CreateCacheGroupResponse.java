package com.alain898.dscache.cache.client.response;

/**
 * Created by alain on 16/9/10.
 */
public class CreateCacheGroupResponse {
    private String message;

    public CreateCacheGroupResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
