package io.javaoperatorsdk.operator.longevitytest;

import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration(labelSelector = "app=longevity-test")
public class ConfigMapReconciler
    implements Reconciler<ConfigMap>, EventSourceInitializer<ConfigMap> {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapReconciler.class);

  private static final String DATA_KEY = "key";

  @Inject
  private KubernetesClient client;

  @Override
  public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context<ConfigMap> context) {
    log.debug("Reconciling ConfigMap name: {}, namespace: {}",
        configMap.getMetadata().getName(),
        configMap.getMetadata().getNamespace());

    var maybeSecret = context.getSecondaryResource(Secret.class);
    maybeSecret.ifPresentOrElse(secret -> {
      if (!match(secret, configMap)) {
        log.debug("Updating Secret, name:{}, namespace:{}", configMap.getMetadata().getName(),
            configMap.getMetadata().getName());
        secret.setData(Map.of(DATA_KEY, configMap.getData().get(DATA_KEY)));
        client.resource(secret).replace();
      }
    }, () -> {
      log.debug("Creating Secret, name:{}, namespace:{}", configMap.getMetadata().getName(),
          configMap.getMetadata().getName());
      client.resource(desired(configMap)).create();
    });
    return UpdateControl.noUpdate();
  }

  private boolean match(Secret secret, ConfigMap configMap) {
    return secret.getData().get(DATA_KEY).equals(configMap.getData().get(DATA_KEY));
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<ConfigMap> context) {
    InformerEventSource<Secret, ConfigMap> secretES =
        new InformerEventSource<>(InformerConfiguration.from(Secret.class, context)
            .build(), context);
    return EventSourceInitializer.nameEventSources(secretES);
  }

  private Secret desired(ConfigMap configMap) {
    var secret = new SecretBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(configMap.getMetadata().getName())
            .withNamespace(configMap.getMetadata().getNamespace())
            .withLabels(Map.of("app", "longevity-test"))
            .build())
        .withData(Map.of(DATA_KEY, configMap.getData().get(DATA_KEY)))
        .build();
    secret.addOwnerReference(configMap);
    return secret;
  }

}
