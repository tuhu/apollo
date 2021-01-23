package com.ctrip.framework.apollo.configservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ctrip.framework.apollo.biz.ApolloBizConfig;
import com.ctrip.framework.apollo.common.ApolloCommonConfig;
import com.ctrip.framework.apollo.metaservice.ApolloMetaServiceConfig;

/**
 * Spring boot application entry point
 *
 * @author Jason Song(song_s@ctrip.com)
 */

@EnableAspectJAutoProxy
@EnableAutoConfiguration
@Configuration
@EnableTransactionManagement
@PropertySource(value = {"classpath:configservice.properties"})
@ComponentScan(basePackageClasses = {ApolloCommonConfig.class,
    ApolloBizConfig.class,
    ConfigServiceApplication.class,
    ApolloMetaServiceConfig.class})
public class ConfigServiceApplication {

  public static void main(String[] args) throws Exception {
    SpringApplication.run(ConfigServiceApplication.class, args);
  }

}
