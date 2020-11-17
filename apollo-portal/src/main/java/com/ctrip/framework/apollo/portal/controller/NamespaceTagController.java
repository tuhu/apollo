package com.ctrip.framework.apollo.portal.controller;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.portal.component.PermissionValidator;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.service.NamespaceTagService;
import com.ctrip.framework.apollo.portal.service.ReleaseService;

@RestController
public class NamespaceTagController {
  private final PermissionValidator permissionValidator;
  private final ReleaseService releaseService;
  private final NamespaceTagService namespaceTagService;
  private final ApplicationEventPublisher publisher;
  private final PortalConfig portalConfig;

  public NamespaceTagController(
      final PermissionValidator permissionValidator,
      final ReleaseService releaseService,
      final NamespaceTagService namespaceTagService,
      final ApplicationEventPublisher publisher,
      final PortalConfig portalConfig) {
    this.permissionValidator = permissionValidator;
    this.releaseService = releaseService;
    this.namespaceTagService = namespaceTagService;
    this.publisher = publisher;
    this.portalConfig = portalConfig;
  }
  
  @GetMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branchs")
  public List<NamespaceBO> findTagBranchs(@PathVariable String appId,
                                @PathVariable String env,
                                @PathVariable String clusterName,
                                @PathVariable String namespaceName) {
	  List<NamespaceBO> namespaceBOs = namespaceTagService.findTagBranchs(appId, Env.valueOf(env), clusterName, namespaceName);
	  for(NamespaceBO namespaceBO : namespaceBOs) {
		  if (namespaceBO != null && permissionValidator.shouldHideConfigToCurrentUser(appId, env, namespaceName)) {
			  namespaceBO.hideItems();
	      }
	  }

	  return namespaceBOs;
  }
  
  @GetMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branch/:tag")
  public NamespaceBO findTagBranch(@PathVariable String appId,
                                @PathVariable String env,
                                @PathVariable String clusterName,
                                @PathVariable String namespaceName,
                                @PathVariable String tag) {
	  NamespaceBO namespaceBO = namespaceTagService.findTagBranch(appId, Env.valueOf(env), clusterName, namespaceName, tag);
	  
	  if (namespaceBO != null && permissionValidator.shouldHideConfigToCurrentUser(appId, env, namespaceName)) {
		  namespaceBO.hideItems();
      }

	  return namespaceBO;
  }

  @PreAuthorize(value = "@permissionValidator.hasModifyNamespacePermission(#appId, #namespaceName, #env)")
  @PostMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branch/{tag}")
  public NamespaceDTO createTagBranch(@PathVariable String appId,
                                   @PathVariable String env,
                                   @PathVariable String clusterName,
                                   @PathVariable String namespaceName,
                                   @PathVariable String tag) {

    return namespaceTagService.createTagBranch(appId, Env.valueOf(env), clusterName, namespaceName, tag);
  }

  @DeleteMapping(value = "/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branch/{branchName}")
  public void deleteTagBranch(@PathVariable String appId,
                           @PathVariable String env,
                           @PathVariable String clusterName,
                           @PathVariable String namespaceName,
                           @PathVariable String branchName) {

    boolean canDelete = permissionValidator.hasReleaseNamespacePermission(appId, namespaceName, env) ||
            (permissionValidator.hasModifyNamespacePermission(appId, namespaceName, env) &&
                      releaseService.loadLatestRelease(appId, Env.valueOf(env), branchName, namespaceName) == null);


    if (!canDelete) {
      throw new AccessDeniedException("Forbidden operation. "
                                      + "Caused by: 1.you don't have release permission "
                                      + "or 2. you don't have modification permission "
                                      + "or 3. you have modification permission but branch has been released");
    }

    namespaceTagService.deleteTagBranch(appId, Env.valueOf(env), clusterName, namespaceName, branchName);

  }  
	  
	  
	  
}
