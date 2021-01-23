package com.ctrip.framework.apollo.biz.tagReleaseRule;

import com.google.common.base.Strings;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class TagReleaseRuleCache implements Comparable<TagReleaseRuleCache> {
  private long ruleId;
  private String branchName;
  private String namespaceName;
  private long releaseId;
  private long loadVersion;
  private int branchStatus;
  private String clientAppId;
  private String tag;

  public TagReleaseRuleCache(long ruleId, String branchName, String namespaceName, long
      releaseId, int branchStatus, long loadVersion, String clientAppId, String tag) {
    this.ruleId = ruleId;
    this.branchName = branchName;
    this.namespaceName = namespaceName;
    this.releaseId = releaseId;
    this.branchStatus = branchStatus;
    this.loadVersion = loadVersion;
    this.clientAppId = clientAppId;
    this.tag = tag;
  }

  public long getRuleId() {
    return ruleId;
  }
  
  public String getClientAppId() {
	return clientAppId;
  }

  public String getTag() {
	return tag;
  }

  public String getBranchName() {
    return branchName;
  }

  public int getBranchStatus() {
    return branchStatus;
  }

  public long getReleaseId() {
    return releaseId;
  }

  public long getLoadVersion() {
    return loadVersion;
  }

  public void setLoadVersion(long loadVersion) {
    this.loadVersion = loadVersion;
  }

  public String getNamespaceName() {
    return namespaceName;
  }
  
  public boolean matches(String clientAppId, String appTag) {
	if(!Strings.isNullOrEmpty(this.clientAppId) && 
			!Strings.isNullOrEmpty(this.tag)) {
		if(this.clientAppId.equals(clientAppId) && this.tag.equals(appTag)) {
	    	return true;
	    }
	}  
    return false;
  }

  @Override
  public int compareTo(TagReleaseRuleCache that) {
    return Long.compare(this.ruleId, that.ruleId);
  }
}
