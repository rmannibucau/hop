/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.kubernetes.util;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;

/** Utility class to build a Kubernetes client from a Hop Kubernetes connection. */
public final class KubernetesUtil {
  private KubernetesUtil() {
    // no-op
  }

  /**
   * Create an authenticated Kubernetes client from the given connection.
   *
   * <p>The precedence for the connection parameters is:
   *
   * <ol>
   *   <li>Explicit fields of the connection (master, token, certificates...) when set.
   *   <li>kubeconfig file (for the host, credentials and namespace/context), when set.
   *   <li>In-cluster service account (auto-discovered), as a fallback.
   * </ol>
   *
   * @param connection the Kubernetes connection metadata
   * @param variables optional variables used to resolve the connection fields
   * @return a configured (and open) Kubernetes client; caller is responsible for closing it
   */
  public static KubernetesClient createClient(
      final KubernetesConnection connection, final IVariables variables) {

    final IVariables vars = variables == null ? new Variables() : variables;
    final KubernetesClientConfiguration config = new KubernetesClientConfiguration();

    applyKubeconfig(config, connection, vars);
    applyExplicitFields(config, connection, vars);

    final long timeoutMs = Math.max(connection.getConnectTimeout() * 1000L, 1_000L);
    config.setClientCustomizer(
        builder ->
            builder
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL));

    return new KubernetesClient(config);
  }

  private static void applyKubeconfig(
      final KubernetesClientConfiguration config,
      final KubernetesConnection connection,
      final IVariables vars) {
    String kubeconfigFile = vars.resolve(connection.getKubeconfigFile());
    if (Utils.isEmpty(kubeconfigFile)) {
      return;
    }
    if (kubeconfigFile.startsWith("~")) {
      kubeconfigFile = System.getProperty("user.home", ".") + kubeconfigFile.substring(1);
    }
    config.setKubeconfig(Paths.get(kubeconfigFile));
  }

  private static void applyExplicitFields(
      final KubernetesClientConfiguration config,
      final KubernetesConnection connection,
      final IVariables vars) {

    final String master = vars.resolve(connection.getMaster());
    if (!Utils.isEmpty(master)) {
      config.setMaster(master);
    }

    final String tokenFile = vars.resolve(connection.getToken());
    if (!Utils.isEmpty(tokenFile)) {
      config.setToken(tokenFile);
    }

    final String caCertFile = vars.resolve(connection.getCaCertFile());
    if (!Utils.isEmpty(caCertFile)) {
      config.setCertificates(readFile(caCertFile));
    }

    final String clientCertFile = vars.resolve(connection.getClientCertFile());
    final String clientKeyFile = vars.resolve(connection.getClientKeyFile());
    if (!Utils.isEmpty(clientCertFile) && !Utils.isEmpty(clientKeyFile)) {
      config.setPrivateKeyCertificate(readFile(clientCertFile));
      config.setPrivateKey(readFile(clientKeyFile));
    }

    config.setSkipTls(connection.isSkipTls());
  }

  private static String readFile(final String path) {
    try {
      return Files.readString(Path.of(path), StandardCharsets.UTF_8).trim();
    } catch (final IOException e) {
      throw new IllegalArgumentException(
          "Unable to read Kubernetes credential file [" + path + "]", e);
    }
  }

  /**
   * Static list of codec {@link Constructor}s (no shared state). Each {@link JsonMapper} instance
   * instantiates its own codecs and must be closed once done.
   */
  private static final List<Constructor<? extends JsonCodec<?>>> MODEL_CODEC_CONSTRUCTORS =
      discoverModelCodecConstructors();

  /**
   * Create a new {@link JsonMapper} bound to the Kubernetes shared models. The returned mapper is
   * not shared and must be closed by the caller (ideally in a {@code try}-with-resources block).
   */
  public static JsonMapper createMapper() {
    final List<JsonCodec<?>> codecs = new ArrayList<>();
    for (final Constructor<? extends JsonCodec<?>> ctor : MODEL_CODEC_CONSTRUCTORS) {
      try {
        codecs.add(ctor.newInstance());
      } catch (final Exception e) {
        throw new IllegalStateException(
            "Unable to instantiate the Kubernetes JSON codec " + ctor, e);
      }
    }
    return new JsonMapperImpl(codecs, Configuration.of(Map.of()));
  }

  // alternatively we could let fusion generate beans and a module for that but for now it is
  // manageable
  @SuppressWarnings("unchecked")
  private static List<Constructor<? extends JsonCodec<?>>> discoverModelCodecConstructors() {
    final List<Constructor<? extends JsonCodec<?>>> codecs = new ArrayList<>();
    for (final String model :
        List.of(
            "KubernetesObject",
            "KubernetesObjectList",
            "KubernetesObjectMetadata",
            "KubernetesStatus",
            "KubernetesCondition",
            "KubernetesJob",
            "KubernetesJobSpec",
            "KubernetesPodTemplate",
            "KubernetesPodSpec",
            "KubernetesContainer",
            "ApiResource",
            "ApiResourceList",
            "ApiGroup",
            "ApiGroupList",
            "ApiVersionInfo")) {
      try {
        final Class<?> codecClass =
            Class.forName("org.apache.hop.kubernetes.model." + model + "$FusionJsonCodec");
        if (JsonCodec.class.isAssignableFrom(codecClass)) {
          codecs.add((Constructor<? extends JsonCodec<?>>) codecClass.getDeclaredConstructor());
        }
      } catch (final Exception e) {
        throw new IllegalStateException(
            "Unable to create the Kubernetes JSON codec for " + model, e);
      }
    }
    return codecs;
  }
}
