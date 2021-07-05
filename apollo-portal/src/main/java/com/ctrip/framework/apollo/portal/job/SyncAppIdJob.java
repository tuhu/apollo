package com.ctrip.framework.apollo.portal.job;

import com.ctrip.framework.apollo.common.entity.App;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.vo.Organization;
import com.ctrip.framework.apollo.portal.listener.AppCreationEvent;
import com.ctrip.framework.apollo.portal.listener.AppInfoChangedEvent;
import com.ctrip.framework.apollo.portal.service.AppService;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SyncAppIdJob {
    private static final Logger log = LoggerFactory.getLogger(SyncAppIdJob.class);
    private static final CloseableHttpClient httpClient = HttpClientBuilder.create().build();

    @Value("${halley.url:}")
    private String baseUrl;

    @Value("${halley.token:}")
    private String token;

    @Value("${halley.type:}")
    private String type;

    @Value("${job.syncAppId.enable:false}")
    private boolean enable;

    private boolean executing;
    private final AppService appService;
    private final PortalConfig portalConfig;
    private final ApplicationEventPublisher publisher;
    private final RolePermissionService rolePermissionService;
    private final UserService userService;

    public SyncAppIdJob(AppService appService, PortalConfig portalConfig, ApplicationEventPublisher publisher, RolePermissionService rolePermissionService, UserService userService) {
        this.appService = appService;
        this.portalConfig = portalConfig;
        this.publisher = publisher;
        this.rolePermissionService = rolePermissionService;
        this.userService = userService;
    }

    @Scheduled(cron = "${job.syncAppId.cron}")
    public void execute() {
        if (!enable) return;

        if (executing) {
            log.warn("任务进行中，不能重复执行");
        }

        executing = true;

        log.info("开始服务");

        try {
            List<Organization> orgs = portalConfig.organizations();
            String admin = portalConfig.superAdmins().stream().findFirst().orElse("apollo");

            String[] types = type == null ? new String[0] : type.split(" ");

            int index = 0;
            while (true) {
                HttpUriRequest request = new HttpGet(baseUrl + "/api/v1/appid/list?page_size=100&page_num=" + ++index);

                request.setHeader(HttpHeaders.AUTHORIZATION, token);

                try (CloseableHttpResponse httpResponse = httpClient.execute(request)) {
                    if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        log.error("请求失败：" + httpResponse.getStatusLine().getStatusCode() + " " + EntityUtils.toString(httpResponse.getEntity()));

                        break;
                    }

                    GetAppIdResponse response = new ObjectMapper().readValue(httpResponse.getEntity().getContent(), GetAppIdResponse.class);
                    if (response == null ||
                            response.getData() == null ||
                            response.getData().getData() == null ||
                            response.getData().getData().size() < 1) {
                        log.info("没有新的AppId");

                        break;
                    }

                    for (AppId appId : response.getData().getData()) {
                        try {
                            App app = appService.load(appId.getId());
                            if (app == null) {
                                createApp(appId, orgs, types, admin);
                            } else {
                                UpdateApp(appId, app, admin);
                            }
                        } catch (Exception ex) {
                            log.error("创建或更新" + appId.getId() + "失败", ex);
                        }
                    }

                    if (response.getData().getData().size() < 100) break;
                }
            }
        } catch (Exception ex) {
            log.error("同步AppId时失败", ex);
        } finally {
            executing = false;
        }
    }

    private void createApp(AppId appId, List<Organization> orgs, String[] types, String admin) {
        String[] array = appId.getId().split("-");

        String org;
        if (appId.getId().startsWith("ext-") || appId.getId().startsWith("int-")) {
            if (array.length < 3) {
                log.error(appId.getId() + "异常" + appId);

                return;
            }

            org = array[2].toUpperCase();
        } else org = array[0].toUpperCase();

        if (types.length > 0 && !StringUtils.isEmpty(appId.getType()) && Arrays.stream(types).noneMatch(t -> appId.getType().equalsIgnoreCase(t))) {
            log.info(appId.getId() + "类型" + appId.getType() + "不在自动创建列表中");

            return;
        }

        Set<String> admins = getAdmins(appId);
        if (admins.size() == 0) admins = new HashSet<>(Collections.singleton(admin));

        App app = new App();

        app.setAppId(app.getAppId());
        app.setName(appId.getName());
        app.setOwnerName(admins.iterator().next());
        setOwnerEmail(app);
        app.setOrgId(org);
        app.setOrgName(orgs.stream()
                .filter(o -> org.equals(o.getOrgId()))
                .map(Organization::getOrgName)
                .findFirst().orElse(StringUtils.isEmpty(appId.getDepartment()) ? org : appId.getDepartment()));

        App createdApp = appService.createAppInLocal(app);

        publisher.publishEvent(new AppCreationEvent(createdApp));

        rolePermissionService.assignRoleToUsers(RoleUtils.buildAppMasterRoleName(createdApp.getAppId()), admins, admin);
    }

    private Set<String> getAdmins(AppId appId) {
        return appId.getOwners().stream()
                .map(AppId.Human::getUserName)
                .filter(userId -> !StringUtils.isEmpty(userId) &&
                        userService.searchUsers(userId, 0, 100).stream()
                                .anyMatch(u -> u.getUserId().equals(userId)))
                .collect(Collectors.toSet());
    }

    private void setOwnerEmail(App app) {
        app.setOwnerEmail(userService.searchUsers(app.getOwnerName(), 0, 100).stream()
                .filter(u -> u.getUserId().equals(app.getOwnerName()))
                .map(UserInfo::getEmail).findFirst()
                .orElse(app.getOwnerName() + "@tuhu.ad"));
    }

    private void UpdateApp(AppId appId, App app, String admin) {
        Set<String> admins = getAdmins(appId);
        if (admins.size() < 1) return;

        List<String> masters = rolePermissionService.queryUsersWithRole(RoleUtils.buildAppMasterRoleName(appId.getId()))
                .stream().map(UserInfo::getUserId)
                .collect(Collectors.toList());

        Set<String> needAdd = admins.stream().filter(a -> !masters.contains(a))
                .collect(Collectors.toSet());
        if (needAdd.size() > 0) {
            rolePermissionService.assignRoleToUsers(RoleUtils.buildAppRoleName(appId.getId(), "Master"), needAdd, admin);

            log.info(appId.getId() + "添加了管理员" + String.join(",", needAdd));
        }

        Set<String> needRemove = masters.stream().filter(m -> !admins.contains(m))
                .collect(Collectors.toSet());
        if (needRemove.size() > 0) {
            rolePermissionService.removeRoleFromUsers(RoleUtils.buildAppRoleName(appId.getId(), "Master"), needRemove, admin);

            log.info(appId.getId() + "删除了管理员" + String.join(",", needRemove));
        }

        String name = appId.getName();
        if (app == null || admins.contains(app.getOwnerName()) && name.equals(app.getName())) return;

        app.setName(name);

        if (!admins.contains(app.getOwnerName())) {
            app.setOwnerName(admins.iterator().next());

            setOwnerEmail(app);
        }

        app.setDataChangeLastModifiedBy(admin);
        app.setDataChangeLastModifiedTime(new Date());

        appService.updateAppInLocal(app);

        publisher.publishEvent(new AppInfoChangedEvent(appService.updateAppInLocal(app)));
    }
}