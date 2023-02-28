package io.javaoperatorsdk.operator.longevitytest;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration(labelSelector = "type=longevity-test")
public class ConfigMapReconciler
    implements Reconciler<ConfigMap>, EventSourceInitializer<ConfigMap> {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapReconciler.class);

  @Override
  public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context<ConfigMap> context) {
    log.info("Reconciling ConfigMap name: {}, namespace: {}",
        configMap.getMetadata().getName(),
        configMap.getMetadata().getNamespace());

    var maybeSecret = context.getSecondaryResource(Secret.class);
    // maybeSecret.ifPresentOrElse(,()->{
    //
    // });

    return UpdateControl.noUpdate();
  }


  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<ConfigMap> context) {
    InformerEventSource<Secret, ConfigMap> secretES =
        new InformerEventSource<>(InformerConfiguration.from(Secret.class, context)
            .build(), context);
    return EventSourceInitializer.nameEventSources(secretES);
  }
}
