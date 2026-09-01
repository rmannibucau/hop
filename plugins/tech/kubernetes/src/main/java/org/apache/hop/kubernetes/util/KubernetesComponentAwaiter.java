/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.kubernetes.util;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Polls a single Kubernetes component until it becomes "ready", mirroring the per-kind readiness
 * rules used by the component awaiter of the testing tooling:
 *
 * <ul>
 *   <li><b>Deployment</b>{@code (apps/v1)} - ready once a condition {@code Type=Available,
 *       Status=True} is present; failed once {@code Type=ProgressDeadlineExceeded, Status=True}.
 *   <li><b>ReplicaSet / StatefulSet / DaemonSet / ReplicationController</b> - ready once {@code
 *       status.readyReplicas >= status.replicas}.
 *   <li><b>Pod</b>{@code (v1)} - ready once a condition {@code Type=Ready, Status=True} is present;
 *       failed once {@code status.phase == Failed}.
 *   <li><b>Job</b>{@code (batch/v1)} - ready once a condition {@code Type=Complete, Status=True} is
 *       present; failed once {@code Type=FailureTarget, Status=True} or {@code status.failed > 0}.
 *   <li><b>Namespace</b>{@code (v1)} - ready once {@code status.phase == Active}; failed once
 *       {@code status.phase == error}.
 *   <li><b>Service</b>{@code (v1)} - ready once at least one {@code discovery.k8s.io/v1}
 *       EndpointSlice (labeled {@code kubernetes.io/service-name=<name>}) exposes an endpoint with
 *       both {@code ready==true} and {@code serving==true}.
 *   <li><b>Cluster</b> (K6 CRD) - ready once a condition {@code Reason=ClusterIsReady, Status=True}
 *       is present; failed once {@code Reason=ClusterIsReady, Status=False}.
 *   <li><b>k6TestRun</b> and other unknown kinds are <em>not</em> handled by this class (the caller
 *       falls back to its generic {@code awaitField}/{@code awaitValue} mechanism).
 * </ul>
 */
public class KubernetesComponentAwaiter {

  private static final String API = "https://kubernetes.api";

  private final KubernetesApiDiscovery discovery;
  private final KubernetesClient client;
  private final JsonMapper mapper;

  public KubernetesComponentAwaiter(
      final KubernetesApiDiscovery discovery,
      final KubernetesClient client,
      final JsonMapper mapper) {
    this.discovery = discovery;
    this.client = client;
    this.mapper = mapper;
  }

  /** Outcome of an await attempt. */
  public enum Result {
    /** The component reached its ready state before the deadline. */
    READY,
    /** A terminal failed state was observed (job failed, deployment deadline exceeded, ...). */
    FAILED,
    /** No terminal state and not ready by the deadline. */
    TIMEOUT,
    /** The kind is not handled by this awaiter; the caller must fall back. */
    NOT_HANDLED
  }

  /** {@code true} when the given kind is handled by this awaiter. */
  public boolean supports(final String kind) {
    if (kind == null) {
      return false;
    }
    return switch (kind.trim().toLowerCase(Locale.ROOT)) {
      case "deployment",
              "deployments",
              "replicaset",
              "replicasets",
              "statefulset",
              "statefulsets",
              "daemonset",
              "daemonsets",
              "replicationcontroller",
              "replicationcontrollers",
              "pod",
              "pods",
              "job",
              "jobs",
              "namespace",
              "namespaces",
              "service",
              "services",
              "cluster",
              "clusters" ->
          true;
      default -> false;
    };
  }

  /**
   * Await the given component until ready.
   *
   * @param apiVersion optional explicit API group/version; when blank it is auto-detected via
   *     {@link KubernetesApiDiscovery#apiVersionOrGuess(String)}
   * @param kind the (singular or plural) resource kind to await
   * @param namespace the namespace for namespaced resources (ignored for cluster-scoped kinds);
   *     when {@code null} a default namespace is used
   * @param name the resource name
   * @param deadlineMillis absolute deadline after which the wait gives up
   * @param pollMillis delay between two polls
   */
  public Result await(
      final String apiVersion,
      final String kind,
      final String namespace,
      final String name,
      final long deadlineMillis,
      final long pollMillis) {
    if (!supports(kind)) {
      return Result.NOT_HANDLED;
    }
    final String k = kind.trim().toLowerCase(Locale.ROOT);
    final long delay = Math.max(1, pollMillis);
    try {
      while (System.currentTimeMillis() < deadlineMillis) {
        final Map<String, Object> body = get(apiVersion, k, namespace, name);
        if (body != null) {
          final Result result = evaluate(k, body);
          if (result == Result.READY || result == Result.FAILED) {
            return result;
          }
        }
        Thread.sleep(delay);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return Result.TIMEOUT;
  }

  private Map<String, Object> get(
      final String apiVersion, final String kind, final String namespace, final String name) {
    try {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(API + path(apiVersion, kind, namespace, name)))
              .header("Accept", "application/json")
              .GET()
              .build();
      final HttpResponse<String> response = client.send(request);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return null;
      }
      return mapper.fromString(Map.class, response.body());
    } catch (final RuntimeException e) {
      return null;
    }
  }

  private String path(
      final String apiVersion, final String kind, final String namespace, final String name) {
    if (kind.equals("service") || kind.equals("services")) {
      // Services expose readiness through their backing EndpointSlices, not the Service itself.
      final String ns = namespace == null ? "default" : namespace;
      return "/apis/discovery.k8s.io/v1/namespaces/"
          + urlEncode(ns)
          + "/endpointslices?labelSelector="
          + urlEncode("kubernetes.io/service-name=" + name);
    }
    final String resolved = discovery.resourcePath(apiVersion, kind, namespace, name);
    return resolved == null ? "" : resolved;
  }

  private Result evaluate(final String kind, final Map<String, Object> body) {
    return switch (kind) {
      case "service", "services" -> evaluateService(body);
      case "job", "jobs" -> evaluateJob(body);
      case "namespace", "namespaces" -> evaluateNamespace(body);
      case "deployment", "deployments" -> evaluateDeployment(body);
      case "pod", "pods" -> evaluatePod(body);
      case "cluster", "clusters" -> evaluateCluster(body);
      case "replicaset",
              "replicasets",
              "statefulset",
              "statefulsets",
              "daemonset",
              "daemonsets",
              "replicationcontroller",
              "replicationcontrollers" ->
          evaluateScale(body);
      default -> Result.NOT_HANDLED;
    };
  }

  @SuppressWarnings("unchecked")
  private Result evaluateService(final Map<String, Object> body) {
    // EndpointSliceList: { items: [ { endpoints: [ { conditions: { ready, serving }, addresses } ]
    // } ] }
    final Object itemsObj = body.get("items");
    if (!(itemsObj instanceof List)) {
      return Result.NOT_HANDLED;
    }
    for (final Object itemObj : (List<Object>) itemsObj) {
      if (!(itemObj instanceof Map)) {
        continue;
      }
      final Object endpointsObj = ((Map<String, Object>) itemObj).get("endpoints");
      if (!(endpointsObj instanceof List)) {
        continue;
      }
      for (final Object epObj : (List<Object>) endpointsObj) {
        if (!(epObj instanceof Map)) {
          continue;
        }
        final Map<String, Object> ep = (Map<String, Object>) epObj;
        final Object conditionsObj = ep.get("conditions");
        final boolean ready;
        final boolean serving;
        if (conditionsObj instanceof Map) {
          final Map<String, Object> conditions = (Map<String, Object>) conditionsObj;
          ready = Boolean.TRUE.equals(conditions.get("ready"));
          serving = Boolean.TRUE.equals(conditions.get("serving"));
        } else {
          ready = true;
          serving = true;
        }
        final Object addresses = ep.get("addresses");
        if (ready
            && serving
            && addresses instanceof List
            && !((List<Object>) addresses).isEmpty()) {
          return Result.READY;
        }
      }
    }
    return Result.NOT_HANDLED;
  }

  private Result evaluateJob(final Map<String, Object> body) {
    if (conditionTrue(body, "Complete", null)) {
      return Result.READY;
    }
    if (conditionTrue(body, "FailureTarget", null) || failedCount(body) > 0) {
      return Result.FAILED;
    }
    return Result.NOT_HANDLED;
  }

  private Result evaluateNamespace(final Map<String, Object> body) {
    final String phase = pointerValue(body, "status.phase");
    if ("Active".equalsIgnoreCase(phase)) {
      return Result.READY;
    }
    if ("error".equalsIgnoreCase(phase) || "Terminating".equalsIgnoreCase(phase)) {
      return Result.FAILED;
    }
    return Result.NOT_HANDLED;
  }

  private Result evaluateDeployment(final Map<String, Object> body) {
    if (conditionTrue(body, "Available", null)) {
      return Result.READY;
    }
    if (conditionTrue(body, "ProgressDeadlineExceeded", null)) {
      return Result.FAILED;
    }
    return Result.NOT_HANDLED;
  }

  private Result evaluatePod(final Map<String, Object> body) {
    if (conditionTrue(body, "Ready", null)) {
      return Result.READY;
    }
    if ("Failed".equalsIgnoreCase(pointerValue(body, "status.phase"))) {
      return Result.FAILED;
    }
    return Result.NOT_HANDLED;
  }

  private Result evaluateCluster(final Map<String, Object> body) {
    if (conditionTrue(body, null, "ClusterIsReady")) {
      return Result.READY;
    }
    if (conditionFalse(body, null, "ClusterIsReady")) {
      return Result.FAILED;
    }
    return Result.NOT_HANDLED;
  }

  private Result evaluateScale(final Map<String, Object> body) {
    final int replicas = intOrDefault(pointerValue(body, "spec.replicas"), 0);
    final int ready = intOrDefault(pointerValue(body, "status.readyReplicas"), 0);
    return replicas > 0 && ready < replicas ? Result.NOT_HANDLED : Result.READY;
  }

  private boolean conditionTrue(
      final Map<String, Object> body, final String type, final String reason) {
    return conditionState(body, type, reason, "True");
  }

  private boolean conditionFalse(
      final Map<String, Object> body, final String type, final String reason) {
    return conditionState(body, type, reason, "False");
  }

  @SuppressWarnings("unchecked")
  private boolean conditionState(
      final Map<String, Object> body, final String type, final String reason, final String want) {
    final Object status = body.get("status");
    if (!(status instanceof Map)) {
      return false;
    }
    final Object conditions = ((Map<String, Object>) status).get("conditions");
    if (!(conditions instanceof List)) {
      return false;
    }
    for (final Object cObj : (List<Object>) conditions) {
      if (!(cObj instanceof Map)) {
        continue;
      }
      final Map<String, Object> c = (Map<String, Object>) cObj;
      if (type != null && !type.equals(c.get("type"))) {
        continue;
      }
      if (reason != null && !reason.equals(c.get("reason"))) {
        continue;
      }
      if (want.equalsIgnoreCase(String.valueOf(c.get("status")))) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private int failedCount(final Map<String, Object> body) {
    final Object status = body.get("status");
    if (!(status instanceof Map)) {
      return 0;
    }
    final Object failed = ((Map<String, Object>) status).get("failed");
    return failed instanceof Number ? ((Number) failed).intValue() : 0;
  }

  private int intOrDefault(final String value, final int def) {
    if (value == null || value.isBlank()) {
      return def;
    }
    try {
      return (int) Double.parseDouble(value.trim());
    } catch (final NumberFormatException e) {
      return def;
    }
  }

  /** Read a dotted JSON path (e.g. {@code status.phase}) from a decoded body map. */
  @SuppressWarnings("unchecked")
  private String pointerValue(final Map<String, Object> body, final String dottedPath) {
    Object current = body;
    for (final String part : dottedPath.split("\\.")) {
      if (!(current instanceof Map)) {
        return null;
      }
      current = ((Map<String, Object>) current).get(part);
    }
    return current == null ? null : String.valueOf(current);
  }

  private String urlEncode(final String value) {
    return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
