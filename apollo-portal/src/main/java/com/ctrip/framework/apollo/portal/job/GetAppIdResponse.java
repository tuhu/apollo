package com.ctrip.framework.apollo.portal.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GetAppIdResponse {
    private int code;

    private String userMessage;

    private String message;

    private Data data;

    public int getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getMessage() {
        return message;
    }

    public Data getData() {
        return data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<AppId> data;

        private int total;

        @JsonProperty("page_num")
        private int pageNo;

        @JsonProperty("page_size")
        private int pageSize;

        public List<AppId> getData() {
            return data;
        }

        public int getTotal() {
            return total;
        }

        public int getPageNo() {
            return pageNo;
        }

        public int getPageSize() {
            return pageSize;
        }
    }
}
