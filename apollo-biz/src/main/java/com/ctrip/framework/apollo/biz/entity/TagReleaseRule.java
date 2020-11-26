package com.ctrip.framework.apollo.biz.entity;

import com.ctrip.framework.apollo.common.entity.BaseEntity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "TagReleaseRule")
@SQLDelete(sql = "Update TagReleaseRule set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class TagReleaseRule extends BaseEntity{

  @Column(name = "appId", nullable = false)
  private String appId;
  
  @Column(name = "ParentClusterName", nullable = false)
  private String parentClusterName;

  @Column(name = "ClusterName", nullable = false)
  private String clusterName;

  @Column(name = "NamespaceName", nullable = false)
  private String namespaceName;

  @Column(name = "BranchName", nullable = false)
  private String branchName;

  @Column(name = "Tag")
  private String tag;

  @Column(name = "releaseId", nullable = false)
  private Long releaseId;

  @Column(name = "BranchStatus", nullable = false)
  private int branchStatus;

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }
  
  public String getParentClusterName() {
	return parentClusterName;
  }
	
  public void setParentClusterName(String parentClusterName) {
	this.parentClusterName = parentClusterName;
  }
	
  public String getClusterName() {
	return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public void setNamespaceName(String namespaceName) {
    this.namespaceName = namespaceName;
  }

  public String getBranchName() {
    return branchName;
  }

  public void setBranchName(String branchName) {
    this.branchName = branchName;
  }

  public String getTag() {
	return tag;
  }

  public void setTag(String tag) {
	this.tag = tag;
  }

  public Long getReleaseId() {
    return releaseId;
  }

  public void setReleaseId(Long releaseId) {
    this.releaseId = releaseId;
  }

  public int getBranchStatus() {
    return branchStatus;
  }

  public void setBranchStatus(int branchStatus) {
    this.branchStatus = branchStatus;
  }
}
