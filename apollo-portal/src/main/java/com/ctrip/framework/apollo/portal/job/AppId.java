package com.ctrip.framework.apollo.portal.job;

import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppId {
    private String id;

    @JsonProperty("appidDepartment")
    private String department;

    private List<Human> owners;

    private String desc;

    @JsonProperty("appidType")
    private String type;

    public String getId() {
        return id;
    }

    public String getDepartment() {
        return department;
    }

    public List<Human> getOwners() {
        return owners;
    }

    public String getDesc() {
        return desc;
    }

    public String getName() {
        return StringUtils.isBlank(desc) || "-".equals(desc) ? id : desc;
    }

    public String getType() {
        return type;
    }

    public String toString() {
        return "AppId(id=" + this.getId() + ", department=" + this.getDepartment() + ", owners=" + Arrays.toString(this.getOwners().stream().map(Human::getUserName).toArray(String[]::new)) + ", desc=" + this.getDesc() + ", type=" + this.getType() + ")";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Human {
        private int id;

        @JsonProperty("username")
        private String userName;

        public int getId() {
            return id;
        }

        public String getUserName() {
            return userName;
        }
    }
}
