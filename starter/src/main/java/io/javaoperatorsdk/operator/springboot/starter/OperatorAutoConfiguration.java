package io.javaoperatorsdk.operator.springboot.starter;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.config.runtime.AnnotationConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OperatorConfigurationProperties.class})
public class OperatorAutoConfiguration extends AbstractConfigurationService {
  @Autowired private OperatorConfigurationProperties configuration;

  public OperatorAutoConfiguration() {
    super(Utils.loadFromProperties());
  }

  @Bean
  @ConditionalOnMissingBean
  public KubernetesClient kubernetesClient() {
    final var config = getClientConfiguration();
    return configuration.getClient().isOpenshift()
        ? new DefaultOpenShiftClient(config)
        : new DefaultKubernetesClient(config);
  }

  @Override
  public Config getClientConfiguration() {
    final var clientCfg = configuration.getClient();
    ConfigBuilder config = new ConfigBuilder();
    config.withTrustCerts(clientCfg.isTrustSelfSignedCertificates());
    clientCfg.getMasterUrl().ifPresent(config::withMasterUrl);
    clientCfg.getUsername().ifPresent(config::withUsername);
    clientCfg.getPassword().ifPresent(config::withPassword);
    return config.build();
  }

  @Override
  public boolean checkCRDAndValidateLocalModel() {
    return configuration.getCheckCrdAndValidateLocalModel();
  }

  @Bean
  @ConditionalOnMissingBean(Operator.class)
  public Operator operator(
      KubernetesClient kubernetesClient,
      List<ResourceController<?>> resourceControllers) {
    Operator operator = new Operator(kubernetesClient, this);
    
    resourceControllers.forEach(r -> operator.register(processController(r)));
    
    return operator;
  }

  private ResourceController<?> processController(ResourceController<?> controller) {
    final var controllerPropertiesMap = configuration.getControllers();
    final var name = ControllerUtils.getNameFor(controller);
    var controllerProps = controllerPropertiesMap.get(name);
    register(new ConfigurationWrapper<>(controller, controllerProps));
    return controller;
  }

  private static class ConfigurationWrapper<R extends CustomResource<?, ?>>
      extends AnnotationConfiguration<R> {
    private final Optional<ControllerProperties> properties;

    private ConfigurationWrapper(
        ResourceController<R> controller, ControllerProperties properties) {
      super(controller);
      this.properties = Optional.ofNullable(properties);
    }

    @Override
    public String getName() {
      return super.getName();
    }

    @Override
    public String getCRDName() {
      return properties.map(ControllerProperties::getCRDName).orElse(super.getCRDName());
    }

    @Override
    public String getFinalizer() {
      return properties.map(ControllerProperties::getFinalizer).orElse(super.getFinalizer());
    }

    @Override
    public boolean isGenerationAware() {
      return properties
          .map(ControllerProperties::isGenerationAware)
          .orElse(super.isGenerationAware());
    }

    @Override
    public Class<R> getCustomResourceClass() {
      return super.getCustomResourceClass();
    }

    @Override
    public Set<String> getNamespaces() {
      return properties.map(ControllerProperties::getNamespaces).orElse(super.getNamespaces());
    }

    @Override
    public boolean watchAllNamespaces() {
      return super.watchAllNamespaces();
    }

    @Override
    public RetryConfiguration getRetryConfiguration() {
      return properties
          .map(ControllerProperties::getRetry)
          .map(RetryProperties::asRetryConfiguration)
          .orElse(RetryConfiguration.DEFAULT);
    }
  }

  @Override
  public int concurrentReconciliationThreads() {
    return configuration.getConcurrentReconciliationThreads();
  }
}
