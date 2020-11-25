package com.ctrip.framework.apollo.adminservice.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.entity.TagNamespace;
import com.ctrip.framework.apollo.biz.message.MessageSender;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.service.NamespaceService;
import com.ctrip.framework.apollo.biz.service.NamespaceTagService;
import com.ctrip.framework.apollo.biz.utils.ReleaseMessageKeyGenerator;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.dto.TagNamespaceDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;

@RestController
public class NamespaceTagController {

  private final MessageSender messageSender;
  private final NamespaceTagService namespaceTagService;
  private final NamespaceService namespaceService;

  public NamespaceTagController(
      final MessageSender messageSender,
      final NamespaceTagService namespaceTagService,
      final NamespaceService namespaceService) {
    this.messageSender = messageSender;
    this.namespaceTagService = namespaceTagService;
    this.namespaceService = namespaceService;
  }


  @PostMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branch/{tag}")
  public NamespaceDTO createTagBranch(@PathVariable String appId,
                                   @PathVariable String clusterName,
                                   @PathVariable String namespaceName,
                                   @PathVariable String tag,
                                   @RequestParam("operator") String operator) {

    checkNamespace(appId, clusterName, namespaceName);

    Namespace createdBranch = namespaceTagService.createTagBranch(appId, clusterName, namespaceName, tag, operator);

    return BeanUtils.transform(NamespaceDTO.class, createdBranch);
  }

  @Transactional
  @DeleteMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branch/{branchName}")
  public void deleteTagBranch(@PathVariable String appId, @PathVariable String clusterName,
                           @PathVariable String namespaceName, @PathVariable String branchName,
                           @RequestParam("operator") String operator) {

    checkBranch(appId, clusterName, namespaceName, branchName);

    namespaceTagService
        .deleteTagBranch(appId, clusterName, namespaceName, branchName, NamespaceBranchStatus.DELETED, operator);

    messageSender.sendMessage(ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName),
                              Topics.APOLLO_RELEASE_TOPIC);

  }

  @GetMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branches")
  public List<TagNamespaceDTO> loadNamespaceTagBranchs(@PathVariable String appId, @PathVariable String clusterName,
                                          @PathVariable String namespaceName) {

    checkNamespace(appId, clusterName, namespaceName);

    List<TagNamespace> childNamespaces = namespaceTagService.findTagBranchs(appId, clusterName, namespaceName);
    if (childNamespaces == null) {
      return null;
    }
    
    List<TagNamespaceDTO> result = new ArrayList<TagNamespaceDTO>();
    for(TagNamespace ns : childNamespaces) {
    	result.add(BeanUtils.transform(TagNamespaceDTO.class, ns));
    }
    
    return result;
  }
  
  @GetMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/tag/branch/{tag}")
  public TagNamespaceDTO loadNamespaceTagBranch(@PathVariable String appId, @PathVariable String clusterName,
                                          @PathVariable String namespaceName, @PathVariable String tag) {

    checkNamespace(appId, clusterName, namespaceName);

    Namespace childNamespace = namespaceTagService.findTagBranch(appId, clusterName, namespaceName, tag);
    if (childNamespace == null) {
      return null;
    }
    
    TagNamespace tn = new TagNamespace();
	tn.setAppId(childNamespace.getAppId());
	tn.setClusterName(childNamespace.getClusterName());
	tn.setNamespaceName(childNamespace.getNamespaceName());
	tn.setId(childNamespace.getId());
	tn.setDeleted(childNamespace.isDeleted());
	tn.setDataChangeCreatedBy(childNamespace.getDataChangeCreatedBy());
	tn.setDataChangeCreatedTime(childNamespace.getDataChangeCreatedTime());
	tn.setDataChangeLastModifiedTime(childNamespace.getDataChangeLastModifiedTime());
	tn.setDataChangeLastModifiedBy(childNamespace.getDataChangeLastModifiedBy());
	tn.setTag(tag);

    return BeanUtils.transform(TagNamespaceDTO.class, tn);
  }

  private void checkBranch(String appId, String clusterName, String namespaceName, String branchName) {
    //1. check parent namespace
    checkNamespace(appId, clusterName, namespaceName);

    //2. check child namespace
    Namespace childNamespace = namespaceService.findOne(appId, branchName, namespaceName);
    if (childNamespace == null) {
      throw new BadRequestException(String.format("Namespace's branch not exist. AppId = %s, ClusterName = %s, "
                                                  + "NamespaceName = %s, BranchName = %s",
                                                  appId, clusterName, namespaceName, branchName));
    }

  }

  private void checkNamespace(String appId, String clusterName, String namespaceName) {
    Namespace parentNamespace = namespaceService.findOne(appId, clusterName, namespaceName);
    if (parentNamespace == null) {
      throw new BadRequestException(String.format("Namespace not exist. AppId = %s, ClusterName = %s, NamespaceName = %s", appId,
                                                  clusterName, namespaceName));
    }
  }


}
