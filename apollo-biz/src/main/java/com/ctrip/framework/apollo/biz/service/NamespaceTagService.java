package com.ctrip.framework.apollo.biz.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ctrip.framework.apollo.biz.common.BizConstants;
import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.entity.Cluster;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.biz.entity.TagNamespace;
import com.ctrip.framework.apollo.biz.entity.TagReleaseRule;
import com.ctrip.framework.apollo.biz.repository.TagReleaseRuleRepository;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.common.constants.ReleaseOperation;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.ServiceException;
import com.ctrip.framework.apollo.common.utils.UniqueKeyGenerator;

@Service
public class NamespaceTagService {

  private final AuditService auditService;
  private final TagReleaseRuleRepository tagReleaseRuleRepository;
  private final ClusterService clusterService;
  private final ReleaseService releaseService;
  private final NamespaceService namespaceService;
  private final ReleaseHistoryService releaseHistoryService;

  public NamespaceTagService(
      final AuditService auditService,
      final TagReleaseRuleRepository tagReleaseRuleRepository,
      final ClusterService clusterService,
      final @Lazy ReleaseService releaseService,
      final NamespaceService namespaceService,
      final ReleaseHistoryService releaseHistoryService) {
    this.auditService = auditService;
    this.tagReleaseRuleRepository = tagReleaseRuleRepository;
    this.clusterService = clusterService;
    this.releaseService = releaseService;
    this.namespaceService = namespaceService;
    this.releaseHistoryService = releaseHistoryService;
  }

  @Transactional
  public Namespace createTagBranch(String appId, String parentClusterName, String namespaceName, String tag, String operator){

    Cluster parentCluster = clusterService.findOne(appId, parentClusterName);
    if (parentCluster == null || parentCluster.getParentClusterId() != 0) {
      throw new BadRequestException("cluster not exist or illegal cluster");
    }

    //create child cluster
    Cluster childCluster = createChildCluster(appId, parentCluster, namespaceName, operator);

    Cluster createdChildCluster = clusterService.saveWithoutInstanceOfAppNamespaces(childCluster);

    //create child namespace
    Namespace childNamespace = createNamespaceTagBranch(appId, createdChildCluster.getName(),
                                                        namespaceName, operator);
    
    TagReleaseRule oldRule = tagReleaseRuleRepository.findByAppIdAndClusterNameAndNamespaceNameAndTag(appId, parentClusterName, namespaceName, tag);
    if(oldRule != null) {
    	throw new ServiceException("the tag has existed.");
    }
    
    TagReleaseRule rule = new TagReleaseRule();
    rule.setBranchStatus(NamespaceBranchStatus.ACTIVE);
    rule.setReleaseId(0l);
    if(!tag.startsWith(BizConstants.SWIMLANE_TAG_PREFIX)) {
    	tag = BizConstants.SWIMLANE_TAG_PREFIX + tag;
    }
    rule.setTag(tag);
    rule.setAppId(appId);
    rule.setParentClusterName(parentClusterName);
    rule.setClusterName(createdChildCluster.getName());
    rule.setNamespaceName(namespaceName);
    rule.setBranchName(createdChildCluster.getName());
    rule.setDataChangeCreatedBy(operator);
    rule.setDataChangeLastModifiedBy(operator);
    
    tagReleaseRuleRepository.save(rule);
    
    return namespaceService.save(childNamespace);
  }

  public List<TagNamespace> findTagBranchs(String appId, String parentClusterName, String namespaceName) {
	List<TagNamespace> result = new ArrayList<TagNamespace>();
	List<Namespace> nps = namespaceService.findChildNamespaces(appId, parentClusterName, namespaceName);
	if(nps != null && !nps.isEmpty()) {
		for(Namespace np : nps) {
			TagNamespace tn = new TagNamespace();
			tn.setAppId(np.getAppId());
			tn.setClusterName(np.getClusterName());
			tn.setNamespaceName(np.getNamespaceName());
			tn.setId(np.getId());
			tn.setDeleted(np.isDeleted());
			tn.setDataChangeCreatedBy(np.getDataChangeCreatedBy());
			tn.setDataChangeCreatedTime(np.getDataChangeCreatedTime());
			tn.setDataChangeLastModifiedTime(np.getDataChangeLastModifiedTime());
			tn.setDataChangeLastModifiedBy(np.getDataChangeLastModifiedBy());
			List<TagReleaseRule> tagRules = tagReleaseRuleRepository.findByAppIdAndClusterNameAndNamespaceName(appId, np.getClusterName(), namespaceName);
			if(tagRules != null && !tagRules.isEmpty()) {
				tn.setTag(tagRules.iterator().next().getTag());
				result.add(tn);
			}
		}
	}
	return result;
  }
  
  public Namespace findTagBranch(String appId, String parentClusterName, String namespaceName, String tag) {
    return namespaceService.findChildNamespace(appId, parentClusterName, namespaceName);
  }

  public TagReleaseRule findBranchTagRules(String appId, String clusterName, String namespaceName,
          String branchName) {	  
	  return tagReleaseRuleRepository.findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(appId, clusterName, namespaceName, branchName);
  }

  @Transactional
  public TagReleaseRule updateTagRulesReleaseId(String appId, String clusterName,
                                   String namespaceName, String branchName,
                                   long latestReleaseId, String operator) {
	  TagReleaseRule oldRules = tagReleaseRuleRepository.
        findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(appId, clusterName, namespaceName, branchName);

    if (oldRules == null) {
      return null;
    }

    TagReleaseRule newRules = new TagReleaseRule();
    newRules.setBranchStatus(NamespaceBranchStatus.ACTIVE);
    newRules.setReleaseId(latestReleaseId);
    newRules.setTag(oldRules.getTag());
    newRules.setAppId(oldRules.getAppId());
    newRules.setClusterName(oldRules.getClusterName());
    newRules.setNamespaceName(oldRules.getNamespaceName());
    newRules.setBranchName(oldRules.getBranchName());
    newRules.setDataChangeCreatedBy(operator);
    newRules.setDataChangeLastModifiedBy(operator);

    tagReleaseRuleRepository.save(newRules);

    tagReleaseRuleRepository.delete(oldRules);

    return newRules;
  }
  
  @Transactional
  public void deleteTagBranch(String appId, String clusterName, String namespaceName,
                           String branchName, int branchStatus, String operator) {
    Cluster toDeleteCluster = clusterService.findOne(appId, branchName);
    if (toDeleteCluster == null) {
      return;
    }

    Release latestBranchRelease = releaseService.findLatestActiveRelease(appId, branchName, namespaceName);

    long latestBranchReleaseId = latestBranchRelease != null ? latestBranchRelease.getId() : 0;

    TagReleaseRule oldRules = tagReleaseRuleRepository
	        .findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(appId, clusterName, namespaceName, branchName);
    
    if (oldRules != null) {
    	tagReleaseRuleRepository.delete(oldRules);
    }
    
    //delete branch cluster
    clusterService.delete(toDeleteCluster.getId(), operator);

    int releaseOperation = branchStatus == NamespaceBranchStatus.MERGED ? ReleaseOperation
        .GRAY_RELEASE_DELETED_AFTER_MERGE : ReleaseOperation.ABANDON_GRAY_RELEASE;

    releaseHistoryService.createReleaseHistory(appId, clusterName, namespaceName, branchName, latestBranchReleaseId,
        latestBranchReleaseId, releaseOperation, null, operator);

    auditService.audit("Tag", toDeleteCluster.getId(), Audit.OP.DELETE, operator);
  }

  private Cluster createChildCluster(String appId, Cluster parentCluster,
                                     String namespaceName, String operator) {

    Cluster childCluster = new Cluster();
    childCluster.setAppId(appId);
    childCluster.setParentClusterId(parentCluster.getId());
    childCluster.setName(UniqueKeyGenerator.generate(appId, parentCluster.getName(), namespaceName));
    childCluster.setDataChangeCreatedBy(operator);
    childCluster.setDataChangeLastModifiedBy(operator);

    return childCluster;
  }


  private Namespace createNamespaceTagBranch(String appId, String clusterName, String namespaceName, String operator) {
    Namespace childNamespace = new Namespace();
    childNamespace.setAppId(appId);
    childNamespace.setClusterName(clusterName);
    childNamespace.setNamespaceName(namespaceName);
    childNamespace.setDataChangeLastModifiedBy(operator);
    childNamespace.setDataChangeCreatedBy(operator);
    return childNamespace;
  }

}
