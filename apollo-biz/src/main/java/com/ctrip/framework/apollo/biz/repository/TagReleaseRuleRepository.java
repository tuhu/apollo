package com.ctrip.framework.apollo.biz.repository;

import java.util.List;

import org.springframework.data.repository.PagingAndSortingRepository;

import com.ctrip.framework.apollo.biz.entity.TagReleaseRule;


public interface TagReleaseRuleRepository extends PagingAndSortingRepository<TagReleaseRule, Long> {

  TagReleaseRule findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(String appId, String clusterName,
                                                                                         String namespaceName, String branchName);

  List<TagReleaseRule> findByAppIdAndClusterNameAndNamespaceName(String appId,
                                                               String clusterName, String namespaceName);

  List<TagReleaseRule> findFirst500ByIdGreaterThanOrderByIdAsc(Long id);

}
