package com.ctrip.framework.apollo.openapi.v1.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.common.utils.RequestPrecondition;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.openapi.auth.ConsumerPermissionValidator;
import com.ctrip.framework.apollo.openapi.dto.OpenNamespaceDTO;
import com.ctrip.framework.apollo.openapi.util.OpenApiBeanUtils;
import com.ctrip.framework.apollo.portal.entity.bo.TagNamespaceBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.service.NamespaceTagService;
import com.ctrip.framework.apollo.portal.service.ReleaseService;
import com.ctrip.framework.apollo.portal.spi.UserService;

@RestController("openapiNamespaceTagController")
@RequestMapping("/openapi/v1/envs/{env}")
public class NamespaceTagController {

    private final ConsumerPermissionValidator consumerPermissionValidator;
    private final ReleaseService releaseService;
    private final NamespaceTagService namespaceTagService;
    private final UserService userService;

    public NamespaceTagController(
        final ConsumerPermissionValidator consumerPermissionValidator,
        final ReleaseService releaseService,
        final NamespaceTagService namespaceTagService,
        final UserService userService) {
        this.consumerPermissionValidator = consumerPermissionValidator;
        this.releaseService = releaseService;
        this.namespaceTagService = namespaceTagService;
        this.userService = userService;
    }

    @GetMapping(value = "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches")
    public OpenNamespaceDTO findTagBranch(@PathVariable String appId,
                                       @PathVariable String env,
                                       @PathVariable String clusterName,
                                       @PathVariable String namespaceName,
                                       @PathVariable String tag) {
    	TagNamespaceBO namespaceBO = namespaceTagService.findTagBranch(appId, Env.valueOf(env.toUpperCase()), clusterName, namespaceName, tag);
        if (namespaceBO == null) {
            return null;
        }
        return OpenApiBeanUtils.transformFromTagNamespaceBO(namespaceBO);
    }

    @PreAuthorize(value = "@consumerPermissionValidator.hasCreateNamespacePermission(#request, #appId)")
    @PostMapping(value = "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/tags/{tag}")
    public OpenNamespaceDTO createTagBranch(@PathVariable String appId,
                                         @PathVariable String env,
                                         @PathVariable String clusterName,
                                         @PathVariable String namespaceName,
                                         @PathVariable String tag,
                                         @RequestParam("operator") String operator,
                                         HttpServletRequest request) {
        RequestPrecondition.checkArguments(!StringUtils.isContainEmpty(operator),"operator can not be empty");

        if (userService.findByUserId(operator) == null) {
            throw new BadRequestException("operator " + operator + " not exists");
        }

        NamespaceDTO namespaceDTO = namespaceTagService.createTagBranch(appId, Env.valueOf(env.toUpperCase()), clusterName, namespaceName, operator);
        if (namespaceDTO == null) {
            return null;
        }
        return BeanUtils.transform(OpenNamespaceDTO.class, namespaceDTO);
    }

    @PreAuthorize(value = "@consumerPermissionValidator.hasModifyNamespacePermission(#request, #appId, #namespaceName, #env)")
    @DeleteMapping(value = "/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/branches/{branchName}")
    public void deleteTagBranch(@PathVariable String appId,
                             @PathVariable String env,
                             @PathVariable String clusterName,
                             @PathVariable String namespaceName,
                             @PathVariable String branchName,
                             @RequestParam("operator") String operator,
                             HttpServletRequest request) {
        RequestPrecondition.checkArguments(!StringUtils.isContainEmpty(operator),"operator can not be empty");

        if (userService.findByUserId(operator) == null) {
            throw new BadRequestException("operator " + operator + " not exists");
        }

        boolean canDelete = consumerPermissionValidator.hasReleaseNamespacePermission(request, appId, namespaceName, env) ||
            (consumerPermissionValidator.hasModifyNamespacePermission(request, appId, namespaceName, env) &&
                releaseService.loadLatestRelease(appId, Env.valueOf(env), branchName, namespaceName) == null);

        if (!canDelete) {
            throw new AccessDeniedException("Forbidden operation. "
                + "Caused by: 1.you don't have release permission "
                + "or 2. you don't have modification permission "
                + "or 3. you have modification permission but branch has been released");
        }
        namespaceTagService.deleteTagBranch(appId, Env.valueOf(env.toUpperCase()), clusterName, namespaceName, branchName, operator);

    }
    
}
