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
 *
 */

package org.apache.hop.workflow.actions.kubernetes;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.pointer.GenericJsonPointer;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.hop.core.Result;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.kubernetes.model.KubernetesObjectList;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Shared helpers for the Kubernetes workflow actions. */
public final class KubernetesActionUtil {

  /**
   * Polling delay (ms) used by the {@code await} helpers. Configurable via the {@code
   * org.apache.hop.k8s.pollMillis} system property (e.g. set to {@code 1} in Surefire to keep the
   * test-suite fast) and read statically so it stays constant for the lifetime of the actions.
   */
  private static final int POLL_MILLIS =
      Math.max(1, Integer.getInteger("org.apache.hop.k8s.pollMillis", 50));

  private KubernetesActionUtil() {
    // utility class
  }

  /** Map a kind to its plural form: {@code y} to {@code ies}, otherwise lowercase + {@code s}. */
  public static String getPlural(String kind) {
    String k = kind.toLowerCase(Locale.ROOT);
    if (k.endsWith("y")) {
      return k.substring(0, k.length() - 1) + "ies";
    }
    return k + "s";
  }

  /** True for resources that live in the cluster scope rather than a namespace. */
  public static boolean isClusterScoped(String kind) {
    switch (kind) {
      case "Node":
      case "Namespace":
      case "PersistentVolume":
      case "StorageClass":
      case "ClusterRole":
      case "ClusterRoleBinding":
      case "CustomResourceDefinition":
      case "PodSecurityPolicy":
        return true;
      default:
        return false;
    }
  }

  /** Determine a sensible default group/version for a well known kind. */
  public static String defaultApiVersion(String kind) {
    switch (kind) {
      case "Deployment":
      case "StatefulSet":
      case "DaemonSet":
        return "apps/v1";
      case "Job":
      case "CronJob":
        return "batch/v1";
      case "Ingress":
        return "networking.k8s.io/v1";
      case "PodSecurityPolicy":
        return "policy/v1beta1";
      default:
        return "v1";
    }
  }

  /**
   * Compute the resource path for a given resource. The apiVersion is expected to be of the form
   * group/version (e.g. apps/v1, batch/v1) or just version (e.g. v1).
   *
   * @param apiVersion the apiVersion of the resource
   * @param kind the kind of the resource
   * @param namespace the namespace or null for cluster scoped resources
   * @param name the name of the resource or null to target the collection
   * @return the API path starting with a slash
   */
  public static String resourcePath(String apiVersion, String kind, String namespace, String name) {
    int slash = apiVersion.indexOf('/');
    if (slash < 0) {
      // core group, version only
      StringBuilder sb = new StringBuilder().append("/api/").append(apiVersion);
      if (!isClusterScoped(kind)) {
        sb.append("/namespaces/").append(encode(namespace == null ? "default" : namespace));
      }
      sb.append('/').append(getPlural(kind));
      if (name != null) {
        sb.append('/').append(encode(name));
      }
      return sb.toString();
    }
    String group = apiVersion.substring(0, slash);
    String version = apiVersion.substring(slash + 1);
    StringBuilder sb =
        new StringBuilder().append("/apis/").append(group).append('/').append(version);
    if (!isClusterScoped(kind)) {
      sb.append("/namespaces/").append(encode(namespace == null ? "default" : namespace));
    }
    sb.append('/').append(getPlural(kind));
    if (name != null) {
      sb.append('/').append(encode(name));
    }
    return sb.toString();
  }

  /** URL-encode a single path segment. */
  public static String encode(String value) {
    return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  /**
   * Await until a JSON pointer extracted from the polled resource equals {@code expected} (compared
   * as strings) or until the deadline elapses. This lets any "unknown" kind be awaited, for example
   * a SparkApplication operator CR: pointer {@code /status.applicationState.state} with expected
   * {@code Completed}.
   *
   * @param client the Kubernetes client to poll with
   * @param getter a request that, when sent, returns the resource to evaluate
   * @param pointerField the JSON pointer, starting with {@code /} (e.g. {@code /status.phase})
   * @param expectedValue the expected, stringified value of the pointer target
   * @param deadlineMillis absolute deadline after which the wait gives up
   * @param pollMillis the delay between two polls
   * @return {@code true} when the condition is satisfied before the deadline
   */
  public static boolean await(
      final KubernetesClient client,
      final HttpRequest getter,
      final String pointerField,
      final String expectedValue,
      final long deadlineMillis,
      final long pollMillis) {
    return await(client, getter, pointerField, expectedValue, null, deadlineMillis, pollMillis);
  }

  /**
   * Await until the polled resource satisfies the given {@code matches} predicate over its decoded
   * body, or until the deadline elapses. When {@code expectedValue} is non-null and no {@code
   * matches} predicate is supplied, plain string equality against the JSON pointer target is used
   * instead.
   *
   * @param matches when non-null, receives the fully decoded JSON body and must return {@code true}
   *     to signal readiness (allows e.g. {@code readyReplicas >= replicas}); when {@code null} the
   *     pointer equality path applies
   */
  public static boolean await(
      final KubernetesClient client,
      final HttpRequest getter,
      final String pointerField,
      final String expectedValue,
      final Predicate<Object> matches,
      final long deadlineMillis,
      final long pollMillis) {
    final GenericJsonPointer pointer = new GenericJsonPointer(pointerField);
    try (final JsonMapper mapper = createMapper()) {
      final HttpRequest accepted = withJsonAccept(getter);
      while (System.currentTimeMillis() < deadlineMillis) {
        final HttpResponse<String> response = client.send(accepted);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          Object body;
          try {
            body = mapper.fromString(Object.class, response.body());
          } catch (final RuntimeException malformed) {
            body = null;
          }
          if (body == null) {
            Thread.sleep(Math.max(1, POLL_MILLIS));
            continue;
          }
          boolean ok = matches != null && matches.test(body);
          if (!ok && matches == null && expectedValue != null) {
            Object value;
            try {
              value = pointer.apply(body);
            } catch (final IllegalStateException missing) {
              value = null;
            }
            ok = value != null && expectedValue.equals(String.valueOf(value));
          }
          if (ok) {
            return true;
          }
        }
        Thread.sleep(Math.max(1, POLL_MILLIS));
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /**
   * Create a new {@link JsonMapper} bound to the Kubernetes shared models. The returned mapper is
   * not shared and must be closed by the caller (ideally in a {@code try}-with-resources block).
   */
  public static JsonMapper createMapper() {
    return KubernetesUtil.createMapper();
  }

  /** Ensures the request advertises {@code Accept: application/json} so Kubernetes returns JSON. */
  public static HttpRequest withJsonAccept(final HttpRequest request) {
    final Optional<String> accept = request.headers().firstValue("Accept");
    if (accept.isPresent() && !accept.get().isEmpty() && !accept.get().equals("*/*")) {
      return request;
    }
    return HttpRequest.newBuilder(request, (name, value) -> true)
        .header("Accept", "application/json")
        .build();
  }

  /**
   * Resolve a namespace, honoring a configured one (explicit or connection) first, then the
   * client's resolved one (in-cluster or kubeconfig), then "default".
   */
  public static String resolveNamespace(
      final String configuredNamespace,
      final KubernetesConnection connection,
      final KubernetesClient client) {
    String ns = configuredNamespace;
    if (Utils.isEmpty(ns) && connection != null) {
      ns = connection.getNamespace();
    }
    if (Utils.isEmpty(ns) && client != null) {
      ns = client.namespace().orElse(null);
    }
    return Utils.isEmpty(ns) ? "default" : ns;
  }

  /**
   * Append a structured result line ({@code status ok|ko}, namespace, name) to the workflow result
   * text so downstream workflows can reuse it via the log text.
   */
  public static void expose(
      final Result result, final boolean ok, final String namespace, final String name) {
    final String prefix = result.getLogText() == null ? "" : result.getLogText() + "\n";
    result.setLogText(
        prefix
            + "status="
            + (ok ? "ok" : "ko")
            + " namespace="
            + (namespace == null ? "" : namespace)
            + " name="
            + (name == null ? "" : name));
  }

  /**
   * Load the connection of the given name. On failure, logs the error, marks the result as failed
   * and returns {@code null}.
   */
  public static KubernetesConnection resolveConnection(
      final String connectionName,
      final IHopMetadataProvider metadataProvider,
      final ILogChannel logChannel,
      final Result result,
      final String errorKeyPrefix) {
    if (metadataProvider == null) {
      logChannel.logError(
          BaseMessages.getString(
              KubernetesActionUtil.class, errorKeyPrefix + ".Error.NoMetadataProvider"));
      result.setNrErrors(1);
      return null;
    }
    final KubernetesConnection connection;
    try {
      connection = metadataProvider.getSerializer(KubernetesConnection.class).load(connectionName);
    } catch (final Exception e) {
      logChannel.logError(
          BaseMessages.getString(
              KubernetesActionUtil.class,
              errorKeyPrefix + ".Error.ConnectionNotFound",
              connectionName));
      result.setNrErrors(1);
      result.setResult(false);
      return null;
    }
    if (connection == null) {
      logChannel.logError(
          BaseMessages.getString(
              KubernetesActionUtil.class,
              errorKeyPrefix + ".Error.ConnectionNotFound",
              connectionName));
      result.setNrErrors(1);
      result.setResult(false);
      return null;
    }
    return connection;
  }

  /**
   * Resolve a single resource name: the given {@code name} when set, otherwise the name of the only
   * resource matching {@code labelSelector}. Returns {@code null} when neither a name nor a
   * selector is provided; throws when the selector matches zero or more than one resource.
   */
  public static String resolveResourceName(
      final KubernetesClient client,
      final String apiVersion,
      final String kind,
      final String namespace,
      final String name,
      final String labelSelector,
      final JsonMapper mapper)
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
            + URLEncoder.encode(labelSelector, StandardCharsets.UTF_8);
    final HttpResponse<String> resp =
        client.send(
            withJsonAccept(
                HttpRequest.newBuilder()
                    .uri(URI.create("https://kubernetes.api" + listPath))
                    .GET()
                    .build()),
            HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new IllegalStateException(
          "Unable to list [" + kind + "] by labelSelector: HTTP " + resp.statusCode());
    }
    final KubernetesObjectList parsed = mapper.fromString(KubernetesObjectList.class, resp.body());
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
}
