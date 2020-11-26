package com.ctrip.framework.apollo.biz.tagReleaseRule;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.entity.TagReleaseRule;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageListener;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.repository.TagReleaseRuleRepository;
import com.ctrip.framework.apollo.common.constants.NamespaceBranchStatus;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class TagReleaseRulesHolder implements ReleaseMessageListener, InitializingBean {
  private static final Logger logger = LoggerFactory.getLogger(TagReleaseRulesHolder.class);
  private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);
  private static final Splitter STRING_SPLITTER =
      Splitter.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR).omitEmptyStrings();

  @Autowired
  private TagReleaseRuleRepository tagReleaseRuleRepository;
  @Autowired
  private BizConfig bizConfig;

  private int databaseScanInterval;
  private ScheduledExecutorService executorService;
  //store configAppId+configCluster+configNamespace -> TagReleaseRuleCache map
  private Multimap<String, TagReleaseRuleCache> TagReleaseRuleCache;
  //store clientAppId+clientNamespace+ip -> ruleId map
  private Multimap<String, Long> reversedTagReleaseRuleCache;
  //an auto increment version to indicate the age of rules
  private AtomicLong loadVersion;

  public TagReleaseRulesHolder() {
    loadVersion = new AtomicLong();
    TagReleaseRuleCache = Multimaps.synchronizedSetMultimap(
        TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, Ordering.natural()));
    reversedTagReleaseRuleCache = Multimaps.synchronizedSetMultimap(
        TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, Ordering.natural()));
    executorService = Executors.newScheduledThreadPool(1, ApolloThreadFactory
        .create("TagReleaseRulesHolder", true));
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    populateDataBaseInterval();
    //force sync load for the first time
    periodicScanRules();
    executorService.scheduleWithFixedDelay(this::periodicScanRules,
        getDatabaseScanIntervalSecond(), getDatabaseScanIntervalSecond(), getDatabaseScanTimeUnit()
    );
  }

  @Override
  public void handleMessage(ReleaseMessage message, String channel) {
    logger.info("message received - channel: {}, message: {}", channel, message);
    String releaseMessage = message.getMessage();
    if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel) || Strings.isNullOrEmpty(releaseMessage)) {
      return;
    }
    List<String> keys = STRING_SPLITTER.splitToList(releaseMessage);
    //message should be appId+cluster+namespace
    if (keys.size() != 3) {
      logger.error("message format invalid - {}", releaseMessage);
      return;
    }
    String appId = keys.get(0);
    String cluster = keys.get(1);
    String namespace = keys.get(2);

    List<TagReleaseRule> rules = tagReleaseRuleRepository
        .findByAppIdAndClusterNameAndNamespaceName(appId, cluster, namespace);

    mergeTagReleaseRules(rules);
  }

  private void periodicScanRules() {
    Transaction transaction = Tracer.newTransaction("Apollo.TagReleaseRulesScanner",
        "scanTagReleaseRules");
    try {
      loadVersion.incrementAndGet();
      scanTagReleaseRules();
      transaction.setStatus(Transaction.SUCCESS);
    } catch (Throwable ex) {
      transaction.setStatus(ex);
      logger.error("Scan gray release rule failed", ex);
    } finally {
      transaction.complete();
    }
  }

  public Long findReleaseIdFromTagReleaseRule(String clientAppId, String appTag, String
      configAppId, String configCluster, String configNamespaceName) {
    String key = assembleTagReleaseRuleKey(configAppId, configCluster, configNamespaceName);
    if (!TagReleaseRuleCache.containsKey(key)) {
      return null;
    }
    //create a new list to avoid ConcurrentModificationException
    List<TagReleaseRuleCache> rules = Lists.newArrayList(TagReleaseRuleCache.get(key));
    for (TagReleaseRuleCache rule : rules) {
      //check branch status
      if (rule.getBranchStatus() != NamespaceBranchStatus.ACTIVE) {
        continue;
      }
      if (rule.matches(clientAppId, appTag)) {
        return rule.getReleaseId();
      }
    }
    return null;
  }

  /**
   * Check whether there are gray release rules for the clientAppId, clientIp, namespace
   * combination. Please note that even there are gray release rules, it doesn't mean it will always
   * load gray releases. Because gray release rules actually apply to one more dimension - cluster.
   */
  public boolean hasTagReleaseRule(String clientAppId, String appTag, String namespaceName) {
    return reversedTagReleaseRuleCache.containsKey(assembleReversedTagReleaseRuleKey(clientAppId,
        namespaceName, appTag));
  }

  private void scanTagReleaseRules() {
    long maxIdScanned = 0;
    boolean hasMore = true;

    while (hasMore && !Thread.currentThread().isInterrupted()) {
      List<TagReleaseRule> TagReleaseRules = tagReleaseRuleRepository
          .findFirst500ByIdGreaterThanOrderByIdAsc(maxIdScanned);
      if (CollectionUtils.isEmpty(TagReleaseRules)) {
        break;
      }
      mergeTagReleaseRules(TagReleaseRules);
      int rulesScanned = TagReleaseRules.size();
      maxIdScanned = TagReleaseRules.get(rulesScanned - 1).getId();
      //batch is 500
      hasMore = rulesScanned == 500;
    }
  }

  private void mergeTagReleaseRules(List<TagReleaseRule> TagReleaseRules) {
    if (CollectionUtils.isEmpty(TagReleaseRules)) {
      return;
    }
    for (TagReleaseRule TagReleaseRule : TagReleaseRules) {
      if (TagReleaseRule.getReleaseId() == null || TagReleaseRule.getReleaseId() == 0) {
        //filter rules with no release id, i.e. never released
        continue;
      }
      String key = assembleTagReleaseRuleKey(TagReleaseRule.getAppId(), TagReleaseRule
          .getParentClusterName(), TagReleaseRule.getNamespaceName());
      //create a new list to avoid ConcurrentModificationException
      List<TagReleaseRuleCache> rules = Lists.newArrayList(TagReleaseRuleCache.get(key));
      TagReleaseRuleCache oldRule = null;
      for (TagReleaseRuleCache ruleCache : rules) {
        if (ruleCache.getBranchName().equals(TagReleaseRule.getBranchName())) {
          oldRule = ruleCache;
          break;
        }
      }

      //if old rule is null and new rule's branch status is not active, ignore
      if (oldRule == null && TagReleaseRule.getBranchStatus() != NamespaceBranchStatus.ACTIVE) {
        continue;
      }

      //use id comparison to avoid synchronization
      if (oldRule == null || TagReleaseRule.getId() > oldRule.getRuleId()) {
        addCache(key, transformRuleToRuleCache(TagReleaseRule));
        if (oldRule != null) {
          removeCache(key, oldRule);
        }
      } else {
        if (oldRule.getBranchStatus() == NamespaceBranchStatus.ACTIVE) {
          //update load version
          oldRule.setLoadVersion(loadVersion.get());
        } else if ((loadVersion.get() - oldRule.getLoadVersion()) > 1) {
          //remove outdated inactive branch rule after 2 update cycles
          removeCache(key, oldRule);
        }
      }
    }
  }

  private void addCache(String key, TagReleaseRuleCache ruleCache) {
    if (ruleCache.getBranchStatus() == NamespaceBranchStatus.ACTIVE) {
    	reversedTagReleaseRuleCache.put(assembleReversedTagReleaseRuleKey(ruleCache
                .getClientAppId(), ruleCache.getNamespaceName(), ruleCache.getTag()), ruleCache.getRuleId());
    }
    TagReleaseRuleCache.put(key, ruleCache);
  }

  private void removeCache(String key, TagReleaseRuleCache ruleCache) {
    TagReleaseRuleCache.remove(key, ruleCache);
    reversedTagReleaseRuleCache.remove(assembleReversedTagReleaseRuleKey(ruleCache
            .getClientAppId(), ruleCache.getNamespaceName(), ruleCache.getTag()), ruleCache.getRuleId());
  }

  private TagReleaseRuleCache transformRuleToRuleCache(TagReleaseRule TagReleaseRule) {
    TagReleaseRuleCache ruleCache = new TagReleaseRuleCache(TagReleaseRule.getId(),
        TagReleaseRule.getBranchName(), TagReleaseRule.getNamespaceName(), TagReleaseRule
        .getReleaseId(), TagReleaseRule.getBranchStatus(), loadVersion.get(), TagReleaseRule.getAppId(), TagReleaseRule.getTag());

    return ruleCache;
  }

  private void populateDataBaseInterval() {
    databaseScanInterval = bizConfig.grayReleaseRuleScanInterval();
  }

  private int getDatabaseScanIntervalSecond() {
    return databaseScanInterval;
  }

  private TimeUnit getDatabaseScanTimeUnit() {
    return TimeUnit.SECONDS;
  }

  private String assembleTagReleaseRuleKey(String configAppId, String configCluster, String
      configNamespaceName) {
    return STRING_JOINER.join(configAppId, configCluster, configNamespaceName);
  }

  private String assembleReversedTagReleaseRuleKey(String clientAppId, String
      clientNamespaceName, String tag) {
    return STRING_JOINER.join(clientAppId, clientNamespaceName, tag);
  }

}
