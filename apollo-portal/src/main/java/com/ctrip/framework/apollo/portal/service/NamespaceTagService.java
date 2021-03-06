package com.ctrip.framework.apollo.portal.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.dto.TagNamespaceDTO;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.ItemsComparator;
import com.ctrip.framework.apollo.portal.constant.TracerEventType;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.entity.bo.TagNamespaceBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.tracer.Tracer;

@Service
public class NamespaceTagService {
	
	private final ItemsComparator itemsComparator;
	  private final UserInfoHolder userInfoHolder;
	  private final NamespaceService namespaceService;
	  private final ItemService itemService;
	  private final AdminServiceAPI.NamespaceTagAPI namespaceTagAPI;
	  private final ReleaseService releaseService;

	  public NamespaceTagService(
	      final ItemsComparator itemsComparator,
	      final UserInfoHolder userInfoHolder,
	      final NamespaceService namespaceService,
	      final ItemService itemService,
	      final AdminServiceAPI.NamespaceTagAPI namespaceTagAPI,
	      final ReleaseService releaseService) {
	    this.itemsComparator = itemsComparator;
	    this.userInfoHolder = userInfoHolder;
	    this.namespaceService = namespaceService;
	    this.itemService = itemService;
	    this.namespaceTagAPI = namespaceTagAPI;
	    this.releaseService = releaseService;
	  }
	  
	  @Transactional
	  public NamespaceDTO createTagBranch(String appId, Env env, String parentClusterName, String namespaceName, String tag) {
	    String operator = userInfoHolder.getUser().getUserId();
	    return createTagBranch(appId, env, parentClusterName, namespaceName, tag, operator);
	  }

	  @Transactional
	  public NamespaceDTO createTagBranch(String appId, Env env, String parentClusterName, String namespaceName, String tag, String operator) {
	    NamespaceDTO createdBranch = namespaceTagAPI.createTagBranch(appId, env, parentClusterName, namespaceName, tag,
	            operator);

	    Tracer.logEvent(TracerEventType.CREATE_GRAY_RELEASE, String.format("%s+%s+%s+%s", appId, env, parentClusterName,
	            namespaceName));
	    return createdBranch;

	  }
	  
	  public void deleteTagBranch(String appId, Env env, String clusterName, String namespaceName,
              String branchName) {

	    String operator = userInfoHolder.getUser().getUserId();
	    deleteTagBranch(appId, env, clusterName, namespaceName, branchName, operator);
	  }
	
	  public void deleteTagBranch(String appId, Env env, String clusterName, String namespaceName,
	              String branchName, String operator) {
		  namespaceTagAPI.deleteTagBranch(appId, env, clusterName, namespaceName, branchName, operator);
	
		  Tracer.logEvent(TracerEventType.DELETE_GRAY_RELEASE,
				  String.format("%s+%s+%s+%s", appId, env, clusterName, namespaceName));
	  }
	  
	  public TagNamespaceDTO findTagBranchBaseInfo(String appId, Env env, String clusterName, String namespaceName, String tag) {
	    return namespaceTagAPI.findTagBranch(appId, env, clusterName, namespaceName, tag);
	  }
	  
	  public TagNamespaceBO findTagBranch(String appId, Env env, String clusterName, String namespaceName, String tag) {
		  TagNamespaceDTO namespaceDTO = findTagBranchBaseInfo(appId, env, clusterName, namespaceName, tag);
	    if (namespaceDTO == null) {
	      return null;
	    }
	    NamespaceBO nbo = namespaceService.loadNamespaceBO(appId, env, namespaceDTO.getClusterName(), namespaceName);
	    if (nbo != null) {
			  TagNamespaceBO tno = new TagNamespaceBO();
			  
			  tno.setBaseInfo(namespaceDTO);
			  
			  tno.setComment(nbo.getComment());
			  tno.setConfigHidden(nbo.isConfigHidden());
			  tno.setFormat(nbo.getFormat());
			  tno.setItemModifiedCnt(nbo.getItemModifiedCnt());
			  tno.setItems(nbo.getItems());
			  tno.setParentAppId(nbo.getParentAppId());
			  tno.setPublic(nbo.isPublic());
			  
			  return tno;
		  }
	    return null;
	  }

	  public List<TagNamespaceBO> findTagBranchs(String appId, Env env, String clusterName, String namespaceName) {
		  List<TagNamespaceDTO> dtos = namespaceTagAPI.findTagBranchs(appId, env, clusterName, namespaceName);
		  if(dtos == null || dtos.isEmpty()) {
			  return null;
		  }
		  List<TagNamespaceBO> result = new ArrayList<TagNamespaceBO>();
		  for(TagNamespaceDTO namespaceDTO : dtos) {
			  NamespaceBO nbo = namespaceService.loadNamespaceBO(appId, env, namespaceDTO.getClusterName(), namespaceName);
			  if (nbo != null) {
				  TagNamespaceBO tno = new TagNamespaceBO();
				  
				  tno.setBaseInfo(namespaceDTO);
				  
				  tno.setComment(nbo.getComment());
				  tno.setConfigHidden(nbo.isConfigHidden());
				  tno.setFormat(nbo.getFormat());
				  tno.setItemModifiedCnt(nbo.getItemModifiedCnt());
				  tno.setItems(nbo.getItems());
				  tno.setParentAppId(nbo.getParentAppId());
				  tno.setPublic(nbo.isPublic());
				  result.add(tno);
			  }
		  }
		  return result;
	  }
	
}
