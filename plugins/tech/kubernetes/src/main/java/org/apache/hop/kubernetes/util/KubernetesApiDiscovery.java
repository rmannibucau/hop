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

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.hop.kubernetes.model.ApiGroup;
import org.apache.hop.kubernetes.model.ApiGroupList;
import org.apache.hop.kubernetes.model.ApiResource;
import org.apache.hop.kubernetes.model.ApiResourceList;

/**
 * Resolves the plural name, cluster/namespaced scope and group/version of a Kubernetes kind by
 * interrogating the cluster API discovery endpoints ({@code /api}, {@code /apis}), mirroring the
 * pattern used by {@code ApiPreloader} in Yupiik BundleBee.
 *
 * <p>The discovery result is computed lazily and cached once; any failure to reach an endpoint
 * (404/403/5xx) silently falls back to the hard-coded fallback rules inside this class so
 * well-known kinds still resolve without extra network calls.
 */
public class KubernetesApiDiscovery {

  private record ResourceInfo(String plural, boolean namespaced, String apiVersion) {}

  private static final String FAKE_HOST = "https://kubernetes.api";

  private final KubernetesClient client;
  private final JsonMapper mapper;
  private final ConcurrentMap<String, ResourceInfo> byKind = new ConcurrentHashMap<>();
  private final Map<String, Map<String, ResourceInfo>> byGroup = new HashMap<>();
  private volatile boolean loaded;

  public KubernetesApiDiscovery(final KubernetesClient client, final JsonMapper mapper) {
    this.client = client;
    this.mapper = mapper;
  }

  /** Plural resource name for a kind, discovered first then guessed via {@link #fallbackPlural}. */
  public String pluralOrGuess(final String apiVersion, final String kind) {
    final ResourceInfo info = resolve(apiVersion, kind);
    return info == null ? fallbackPlural(kind) : info.plural();
  }

  /**
   * {@code true} when the kind resolves to a namespaced resource, else via {@link
   * #fallbackClusterScoped}.
   */
  public boolean namespacedOrGuess(final String apiVersion, final String kind) {
    final ResourceInfo info = resolve(apiVersion, kind);
    return info == null ? !fallbackClusterScoped(kind) : info.namespaced();
  }

  /** Group/version for a kind, discovered first then guessed via {@link #fallbackApiVersion}. */
  public String apiVersionOrGuess(final String kind) {
    discovery();
    final String key = kindKey(kind);
    final ResourceInfo exact = byKind.get(key);
    if (exact != null) {
      return exact.apiVersion();
    }
    ResourceInfo chosen = null;
    synchronized (byGroup) {
      for (final Map<String, ResourceInfo> group : byGroup.values()) {
        final ResourceInfo candidate = group.get(key);
        if (candidate != null
            && (chosen == null || candidate.apiVersion().compareTo(chosen.apiVersion()) > 0)) {
          chosen = candidate;
        }
      }
    }
    return chosen == null ? fallbackApiVersion(kind) : chosen.apiVersion();
  }

  /** Build a resource URL path, using discovery when available and the fallback rules otherwise. */
  public String resourcePath(
      final String apiVersion, final String kind, final String namespace, final String name) {
    final ResourceInfo info = resolve(apiVersion, kind);
    final boolean clusterScoped = info != null ? !info.namespaced() : fallbackClusterScoped(kind);
    final String plural = info != null ? info.plural() : fallbackPlural(kind);
    final int slash = apiVersion.indexOf('/');
    if (slash < 0) {
      final StringBuilder sb = new StringBuilder().append("/api/").append(apiVersion);
      if (!clusterScoped) {
        sb.append("/namespaces/").append(encode(namespace == null ? "default" : namespace));
      }
      sb.append('/').append(plural);
      return name == null ? sb.toString() : sb.append('/').append(encode(name)).toString();
    }
    final String group = apiVersion.substring(0, slash);
    final String version = apiVersion.substring(slash + 1);
    final StringBuilder sb =
        new StringBuilder().append("/apis/").append(group).append('/').append(version);
    if (!clusterScoped) {
      sb.append("/namespaces/").append(encode(namespace == null ? "default" : namespace));
    }
    sb.append('/').append(plural);
    return name == null ? sb.toString() : sb.append('/').append(encode(name)).toString();
  }

  private ResourceInfo resolve(final String apiVersion, final String kind) {
    discovery();
    final String key = kindKey(kind);
    final int slash = apiVersion.indexOf('/');
    if (slash < 0) {
      return byGroup.getOrDefault("__core", Map.of()).get(key);
    }
    final String group = apiVersion.substring(0, slash);
    return byGroup.getOrDefault(group, Map.of()).get(key);
  }

  private synchronized void discovery() {
    if (loaded) {
      return;
    }
    final String coreVersion = "v1";
    recordCore(coreVersion, get("/api/" + coreVersion, ApiResourceList.class));
    final ApiGroupList groups = get("/apis", ApiGroupList.class);
    if (groups != null && groups.groups() != null) {
      for (final ApiGroup group : groups.groups()) {
        if (group == null || group.versions() == null) {
          continue;
        }
        for (final var version : group.versions()) {
          final String groupVersion = version.groupVersion();
          final int slash = groupVersion.indexOf('/');
          final String groupName = slash < 0 ? groupVersion : groupVersion.substring(0, slash);
          recordGroup(groupName, groupVersion, get("/apis/" + groupVersion, ApiResourceList.class));
        }
      }
    }
    loaded = true;
  }

  private void recordCore(final String version, final ApiResourceList list) {
    if (list == null || list.resources() == null) {
      return;
    }
    final Map<String, ResourceInfo> core = new HashMap<>();
    for (final ApiResource res : list.resources()) {
      if (res == null || empty(res.name()) || res.name().contains("/")) {
        continue;
      }
      core.put(
          kindKey(empty(res.kind()) ? res.name() : res.kind()),
          new ResourceInfo(res.name(), res.namespaced(), version));
    }
    synchronized (byGroup) {
      byGroup.put("__core", core);
      for (final Map.Entry<String, ResourceInfo> entry : core.entrySet()) {
        byKind.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private void recordGroup(
      final String groupName, final String groupVersion, final ApiResourceList list) {
    if (list == null || list.resources() == null) {
      return;
    }
    final Map<String, ResourceInfo> group = new HashMap<>();
    for (final ApiResource res : list.resources()) {
      if (res == null || empty(res.name()) || res.name().contains("/")) {
        continue;
      }
      group.put(
          kindKey(empty(res.kind()) ? res.name() : res.kind()),
          new ResourceInfo(res.name(), res.namespaced(), groupVersion));
    }
    synchronized (byGroup) {
      byGroup.put(groupName, group);
      group.forEach(byKind::putIfAbsent);
    }
  }

  private <T> T get(final String path, final Class<T> type) {
    try {
      final HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(FAKE_HOST + path))
                  .header("Accept", "application/json")
                  .GET()
                  .build());
      if (response.statusCode() >= 400) {
        return null;
      }
      return mapper.read(type, new java.io.StringReader(response.body()));
    } catch (final RuntimeException e) {
      return null;
    }
  }

  private static String kindKey(final String kind) {
    return kind.toLowerCase(Locale.ROOT);
  }

  private static boolean empty(final String value) {
    return value == null || value.isEmpty();
  }

  /** Fallback plural: lowercase + {@code s}. */
  private static String fallbackPlural(final String kind) {
    final String k = kind.toLowerCase(Locale.ROOT);
    if (k.endsWith("y") && !k.endsWith("ay") && !k.endsWith("ey") && !k.endsWith("oy")) {
      return k.substring(0, k.length() - 1) + "ies";
    }
    return k + "s";
  }

  /** Fallback cluster-scoped kinds (used only when discovery is unavailable). */
  private static boolean fallbackClusterScoped(final String kind) {
    switch (kind) {
      case "Node":
      case "Namespace":
      case "PersistentVolume":
      case "StorageClass":
      case "ClusterRole":
      case "ClusterRoleBinding":
      case "CustomResourceDefinition":
      case "APIService":
        return true;
      default:
        return false;
    }
  }

  /**
   * Fallback group/version for a few well-known kinds (used only when discovery is unavailable).
   */
  private static String fallbackApiVersion(final String kind) {
    switch (kind) {
      case "Deployment":
      case "ReplicaSet":
      case "StatefulSet":
      case "DaemonSet":
        return "apps/v1";
      case "Job":
      case "CronJob":
        return "batch/v1";
      case "Ingress":
        return "networking.k8s.io/v1";
      case "HorizontalPodAutoscaler":
        return "autoscaling/v2";
      case "NetworkPolicy":
        return "networking.k8s.io/v1";
      default:
        return "v1";
    }
  }

  private static String encode(final String value) {
    return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
