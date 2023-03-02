package io.javaoperatorsdk.operator.longevitytest;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import static org.awaitility.Awaitility.await;

@QuarkusMain
public class TestRunner implements QuarkusApplication {

  private static final String TEST_CONFIG_MAP_NAME = "longevity-test-1";

  private static final String DATA_KEY = "key";
  public static final String LABEL_KEY = "app";
  public static final String LABEL_VALUE = "longevity-test";
  public static final String INITIAL_VALUE = "1";

  @Override
  public int run(String... args) {
    try (OpenShiftClient client = new KubernetesClientBuilder().build()
        .adapt(OpenShiftClient.class)) {
      testPrimaryResourceUpdate(client);
      testSecondaryResourceUpdateAndRollback(client);
    }
    return 0;
  }

  private void testPrimaryResourceUpdate(OpenShiftClient client) {
    ConfigMap configMap = client.configMaps().withName(TEST_CONFIG_MAP_NAME).get();
    if (configMap == null) {
      client.resource(testConfigMap()).create();
      waitForSecretCreated(client);
    } else {
      var newValue = updateConfigMap(client, configMap);
      waitUntilValueReflectedInSecret(client, newValue);
    }
  }

  private void waitUntilValueReflectedInSecret(OpenShiftClient client, int value) {
    await().until(() -> {
      Secret secret = client.secrets().withName(TEST_CONFIG_MAP_NAME).get();
      if (secret == null) {
        throw new IllegalStateException("Secret should not be null");
      }
      var secretDataValue = Integer.parseInt(secret.getStringData().get(DATA_KEY));
      if (value != secretDataValue) {
        throw new IllegalStateException("Unexpected value in secret data: " + secretDataValue);
      }
      return true;
    });
  }

  private int updateConfigMap(OpenShiftClient client, ConfigMap configMap) {
    var newValue = Integer.parseInt(configMap.getData().get(DATA_KEY)) + 1;
    configMap.getData().put(DATA_KEY, "" + newValue);
    client.configMaps().resource(configMap).replace();
    return newValue;
  }

  private void waitForSecretCreated(OpenShiftClient client) {
    await().until(() -> {
      Secret secret = client.secrets().withName(TEST_CONFIG_MAP_NAME).get();
      if (secret == null) {
        return false;
      }
      var secretDataValue = secret.getStringData().get(DATA_KEY);
      if (!INITIAL_VALUE.equals(secretDataValue)) {
        throw new IllegalStateException("Unexpected value in secret data: " + secretDataValue);
      }
      return true;
    });
  }

  private void testSecondaryResourceUpdateAndRollback(OpenShiftClient client) {
    var secret = client.secrets().withName(TEST_CONFIG_MAP_NAME).get();
    var actualKey = Integer.parseInt(secret.getStringData().get(DATA_KEY));
    secret.setStringData(Map.of(DATA_KEY, "-1"));
    client.resource(secret).replace();

    await().until(() -> {
      var actualSecret = client.secrets().withName(TEST_CONFIG_MAP_NAME).get();
      return actualKey == Integer.parseInt(actualSecret.getStringData().get(DATA_KEY));
    });
  }


  private ConfigMap testConfigMap() {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(TEST_CONFIG_MAP_NAME)
            .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
            .build())
        .withData(Map.of(DATA_KEY, INITIAL_VALUE))
        .build();
  }

}
