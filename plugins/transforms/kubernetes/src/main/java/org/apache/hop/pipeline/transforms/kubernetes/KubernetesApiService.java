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

package org.apache.hop.pipeline.transforms.kubernetes;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.pointer.GenericJsonPointer;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.kubernetes.model.KubernetesObjectList;
import org.apache.hop.kubernetes.model.KubernetesObjectMetadata;
import org.apache.hop.kubernetes.util.KubernetesApiDiscovery;
import org.apache.hop.kubernetes.util.KubernetesComponentAwaiter;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transform.BaseTransform;

/**
 * Per-transform, {@link AutoCloseable} service that hosts the {@link KubernetesClient} and the
 * {@link JsonMapper} together. It is created by the transform in {@code init()} and closed in
 * {@code dispose()}, mirroring the enclosing transform lifecycle (a Beam {@code DoFn} for example).
 */
public class KubernetesApiService implements AutoCloseable {

  /**
   * Polling delay (ms) used by the {@code await} helpers. Configurable via the {@code
   * org.apache.hop.k8s.pollMillis} system property (e.g. set to {@code 1} in Surefire to keep the
   * test-suite fast) and read statically so it stays constant for the service lifetime.
   */
  private static final int POLL_MILLIS =
      Math.max(1, Integer.getInteger("org.apache.hop.k8s.pollMillis", 20));

  private final KubernetesConnection connection;
  private final IVariables variables;
  private final KubernetesClient client;
  private final JsonMapper mapper;
  private final KubernetesApiDiscovery discovery;
  private final KubernetesComponentAwaiter awaiter;

  public KubernetesApiService(final KubernetesConnection connection, final IVariables variables) {
    this.connection = connection;
    this.variables = variables;
    this.client = KubernetesUtil.createClient(connection, variables);
    this.mapper = KubernetesUtil.createMapper();
    this.discovery = new KubernetesApiDiscovery(client, mapper);
    this.awaiter = new KubernetesComponentAwaiter(discovery, client, mapper);
  }

  /**
   * Create the service for the named connection. On failure, logs and returns {@code null}.
   *
   * @param errorKeyPrefix e.g. {@code "KubernetesDelete"}, the message key {@code
   *     <prefix>.Error.InvalidConnection} is looked up.
   */
  public static KubernetesApiService fromConnection(
      final String connectionName,
      final IHopMetadataProvider metadataProvider,
      final IVariables variables,
      final ILogChannel logChannel,
      final String errorKeyPrefix) {
    final KubernetesConnection connection;
    try {
      connection = metadataProvider.getSerializer(KubernetesConnection.class).load(connectionName);
    } catch (final Exception e) {
      logChannel.logError(
          BaseMessages.getString(
              KubernetesApiService.class,
              errorKeyPrefix + ".Error.InvalidConnection",
              connectionName),
          e);
      return null;
    }
    if (connection == null) {
      logChannel.logError(
          BaseMessages.getString(
              KubernetesApiService.class,
              errorKeyPrefix + ".Error.InvalidConnection",
              connectionName));
      return null;
    }
    return new KubernetesApiService(connection, variables);
  }

  /** Close the given service, ignoring {@code null} (e.g. the per-transform dispose). */
  public static void close(final KubernetesApiService service) {
    if (service != null) {
      service.close();
    }
  }

  /**
   * Route a failing row to the transform error output (when error handling is enabled) so the
   * pipeline recovers instead of aborting. The error counter must be incremented on the transform:
   * kept here only to capture the "how" once.
   */
  public static void failRow(
      final BaseTransform<?, ?> transform, final Object[] row, final String message) {
    try {
      transform.setErrors(transform.getErrors() + 1);
      transform.putError(transform.getInputRowMeta(), row, 1L, message, null, null);
    } catch (final Exception e) {
      transform.logError(message, e);
    }
  }

  public KubernetesClient client() {
    return client;
  }

  /**
   * Resolve a namespace for an operation, honoring a configured one (override or connection) first,
   * then the client's resolved one, then "default".
   */
  public String resolveNamespace(final String override) {
    final IVariables vars =
        variables == null ? new org.apache.hop.core.variables.Variables() : variables;
    final String configured =
        !Utils.isEmpty(override)
            ? vars.resolve(override)
            : (connection.getNamespace() == null ? null : vars.resolve(connection.getNamespace()));
    return !Utils.isEmpty(configured) ? configured : client.namespace().orElse("default");
  }

  /** Parse an unknown/generic JSON payload as a raw map. */
  @SuppressWarnings("unchecked")
  public Map<String, Object> parseObject(final String json) {
    try {
      return (Map<String, Object>) mapper.fromString(Object.class, json);
    } catch (final Exception e) {
      throw new IllegalArgumentException("Unable to parse JSON payload", e);
    }
  }

  /** Parse a JSON payload as a known {@code @JsonModel} type. */
  public <T> T parseObject(final Class<T> type, final String json) {
    try {
      return mapper.fromString(type, json);
    } catch (final Exception e) {
      throw new IllegalArgumentException("Unable to parse JSON payload", e);
    }
  }

  /** Render a value (map / model) back to JSON. */
  public String render(final Object value) {
    try {
      return mapper.toString(value);
    } catch (final Exception e) {
      throw new IllegalArgumentException("Unable to render JSON payload", e);
    }
  }

  /** Extract and type the {@code metadata} block of an object. */
  public KubernetesObjectMetadata metadataOf(final Map<String, Object> object) {
    if (object == null) {
      return null;
    }
    final Object meta = object.get("metadata");
    if (!(meta instanceof Map)) {
      return null;
    }
    return mapper.fromString(KubernetesObjectMetadata.class, mapper.toString(meta));
  }

  public String urlEncode(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public String pluralOf(final String apiVersion, final String kind) {
    return discovery.pluralOrGuess(resolveApiVersion(apiVersion, kind), kind);
  }

  /** Resolve a namespaced resource path for a given (possibly singular) kind. */
  public String resourcePath(
      final String apiVersion, final String kind, final String namespace, final String name) {
    return discovery.resourcePath(resolveApiVersion(apiVersion, kind), kind, namespace, name);
  }

  private String resolveApiVersion(final String apiVersion, final String kind) {
    return (apiVersion == null || apiVersion.isBlank())
        ? discovery.apiVersionOrGuess(kind)
        : apiVersion;
  }

  /**
   * Resolve a single resource name: the given {@code name} when set, otherwise the name of the only
   * resource matching {@code labelSelector}. Returns {@code null} when neither a name nor a
   * selector is provided; throws when the selector matches zero or more than one resource.
   */
  public String resolveResourceName(
      final String apiVersion,
      final String kind,
      final String namespace,
      final String name,
      final String labelSelector)
      throws Exception {
    if (!Utils.isEmpty(name)) {
      return name;
    }
    if (Utils.isEmpty(labelSelector)) {
      return null;
    }
    final String listPath =
        resourcePath(apiVersion, kind, namespace, null)
            + "?labelSelector="
            + urlEncode(labelSelector);
    final String body =
        client()
            .send(
                java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://kubernetes.api" + listPath))
                    .header("Accept", "application/json")
                    .GET()
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())
            .body();
    final KubernetesObjectList parsed = parseObject(KubernetesObjectList.class, body);
    final int size = parsed == null || parsed.items() == null ? 0 : parsed.items().size();
    if (size != 1) {
      throw new IllegalStateException(
          "Expected exactly one resource for labelSelector ["
              + labelSelector
              + "] but found "
              + size);
    }
    return parsed.items().get(0).metadata().name();
  }

  /**
   * Await until the given kind reaches its per-kind ready state (Deployment, Pod, Job, Namespace,
   * Service, Cluster, ...). Unknown kinds are reported via {@link
   * KubernetesComponentAwaiter.Result#NOT_HANDLED} so the caller can fall back to the generic
   * {@link #await(HttpRequest, String, String, int, int)} mechanism.
   */
  public KubernetesComponentAwaiter.Result awaitReady(
      final String apiVersion,
      final String kind,
      final String namespace,
      final String name,
      final long deadlineMillis,
      final long pollMillis) {
    return awaiter.await(apiVersion, kind, namespace, name, deadlineMillis, pollMillis);
  }

  /**
   * Await until a JSON pointer extracted from the polled resource equals {@code expected} (compared
   * as strings) or until the deadline elapses.
   *
   * @param getter a request builder that, when sent, returns the resource to evaluate
   * @param pointerField the JSON pointer, starting with {@code /} (e.g. {@code /status.phase})
   * @param expectedValue the expected, stringified value of the pointer target
   * @param timeoutSeconds how long to wait before giving up
   * @param pollMs the delay between two polls
   * @return {@code true} when the condition is satisfied before the deadline
   */
  public boolean await(
      final HttpRequest getter,
      final String pointerField,
      final String expectedValue,
      final int timeoutSeconds,
      final int pollMs) {
    final GenericJsonPointer pointer = new GenericJsonPointer(pointerField);
    final long deadline = System.currentTimeMillis() + Math.max(0, timeoutSeconds) * 1000L;
    try {
      while (System.currentTimeMillis() < deadline) {
        final HttpResponse<String> response = client.send(getter);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          Object value;
          try {
            value = pointer.apply(parseObject(response.body()));
          } catch (final IllegalStateException missing) {
            value = null;
          }
          if (value != null && String.valueOf(value).equals(expectedValue)) {
            return true;
          }
        }
        Thread.sleep(Math.max(1, POLL_MILLIS));
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to await Kubernetes resource condition", e);
    }
    return false;
  }

  @Override
  public void close() {
    try {
      mapper.close();
    } catch (final RuntimeException e) {
      // ignore
    }
    try {
      client.close();
    } catch (final RuntimeException e) {
      // ignore
    }
  }
}
