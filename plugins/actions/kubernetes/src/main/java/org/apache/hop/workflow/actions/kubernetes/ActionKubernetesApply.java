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
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.io.File;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.kubernetes.model.KubernetesObject;
import org.apache.hop.kubernetes.util.KubernetesApiDiscovery;
import org.apache.hop.kubernetes.util.KubernetesComponentAwaiter;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Apply one or several Kubernetes manifests to a cluster. */
@Action(
    id = "KUBERNETES_APPLY",
    name = "i18n::ActionKubernetesApply.Name",
    description = "i18n::ActionKubernetesApply.Description",
    image = "k8s-apply.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionKubernetesApply.keyword",
    documentationUrl = "/workflow/actions/kubernetes/kubernetes-apply.html")
@Getter
@Setter
public class ActionKubernetesApply extends ActionKubernetesBase {

  private static final Class<?> PKG = ActionKubernetesApply.class;

  @HopMetadataProperty(key = "manifest_file")
  private String manifestFile;

  @HopMetadataProperty(key = "folder")
  private String folder;

  @HopMetadataProperty(key = "inline_manifest")
  private String inlineManifest;

  @HopMetadataProperty(key = "wait_ready")
  private boolean waitReady;

  @HopMetadataProperty(key = "rollout_timeout_seconds")
  private int rolloutTimeoutSeconds;

  @HopMetadataProperty(key = "await_field")
  private String awaitField;

  @HopMetadataProperty(key = "await_value")
  private String awaitValue;

  /**
   * How an existing resource is updated. Defaults to {@link UpdateMode#PATCH}; set to {@link
   * UpdateMode#PUT} to force a full replace.
   */
  @HopMetadataProperty(key = "update_mode")
  private UpdateMode updateMode;

  /** How an existing resource is updated when applying a manifest. */
  public enum UpdateMode {
    /** PATCH with strategic merge first, then merge patch on 415. */
    PATCH {
      @Override
      String contentType(final ApiPhase phase) {
        return phase == ApiPhase.CREATE || phase == ApiPhase.REPLACE
            ? "application/json"
            : "application/strategic-merge-patch+json";
      }
    },
    /** Force a full PUT replacing the resource. */
    PUT {
      @Override
      String contentType(final ApiPhase phase) {
        return "application/json";
      }

      @Override
      String verb(final ApiPhase phase) {
        return phase == ApiPhase.CREATE ? "POST" : "PUT";
      }
    };

    String contentType(final ApiPhase phase) {
      return phase == ApiPhase.CREATE ? "application/json" : "application/merge-patch+json";
    }

    String verb(final ApiPhase phase) {
      return phase == ApiPhase.CREATE ? "POST" : "PATCH";
    }
  }

  /** Which API phase an update applies to; used to pick the content type and HTTP verb. */
  private enum ApiPhase {
    CREATE,
    REPLACE,
    MERGE
  }

  public ActionKubernetesApply() {}

  public ActionKubernetesApply(final String name) {
    this();
    setName(name);
  }

  @Override
  public Result execute(Result previousResult, int nr) throws HopException {
    Result result = previousResult;
    result.setResult(false);

    final KubernetesConnection connection =
        KubernetesActionUtil.resolveConnection(
            connectionName,
            getMetadataProvider(),
            getLogChannel(),
            result,
            "ActionKubernetesApply");
    if (connection == null) {
      return result;
    }

    List<Path> files = new ArrayList<>();
    try {
      if (!Utils.isEmpty(inlineManifest)) {
        files.add(null); // sentinel meaning "inline", handled separately
      } else {
        String mfile = resolve(manifestFile);
        if (!Utils.isEmpty(mfile)) {
          files.add(Path.of(mfile));
        } else {
          String rFolder = resolve(folder);
          if (Utils.isEmpty(rFolder)) {
            logError(BaseMessages.getString(PKG, "ActionKubernetesApply.Error.NoManifestSource"));
            result.setNrErrors(1);
            return result;
          }
          File dir = new File(rFolder);
          if (!dir.isDirectory()) {
            logError(
                BaseMessages.getString(PKG, "ActionKubernetesApply.Error.FolderNotFound", rFolder));
            result.setNrErrors(1);
            return result;
          }
          File[] children = dir.listFiles();
          if (children == null) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionKubernetesApply.Error.FolderNotReadable", rFolder));
            result.setNrErrors(1);
            return result;
          }
          for (File child : children) {
            String name = child.getName().toLowerCase();
            if ((name.endsWith(".yml")
                    || name.endsWith(".yaml")
                    || name.endsWith(".json")
                    || name.endsWith(".tpl"))
                && child.isFile()) {
              files.add(child.toPath());
            }
          }
          files.sort(Comparator.comparing(Path::toString));
        }
      }

      if (files.isEmpty()) {
        logError(BaseMessages.getString(PKG, "ActionKubernetesApply.Error.NoManifestSource"));
        result.setNrErrors(1);
        return result;
      }

      try (KubernetesClient client = KubernetesUtil.createClient(connection, this)) {
        final String ns = resolveNamespace(connection, client);
        if (files.get(0) == null) {
          // inline manifest
          applyManifest(client, resolve(inlineManifest), ns, result);
        } else {
          for (Path file : files) {
            String content = Files.readString(file);
            logBasic(BaseMessages.getString(PKG, "ActionKubernetesApply.Log.ApplyingFile", file));
            applyManifest(client, content, ns, result);
          }
        }
      }
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesApply.Error.UnableToApply"), e);
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return result;
    }

    if (result.getNrErrors() == 0) {
      result.setResult(true);
      KubernetesActionUtil.expose(result, true, resolveNamespace(null, null), "");
    }
    return result;
  }

  private String resolveNamespace(KubernetesConnection connection, KubernetesClient client) {
    return KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, client);
  }

  private void applyManifest(
      KubernetesClient client, String content, String defaultNamespace, Result result) {
    try (final JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      applyManifest(client, content, defaultNamespace, result, mapper);
    } catch (final Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesApply.Log.UnableToApply", "manifest", e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
    }
  }

  private void applyManifest(
      KubernetesClient client,
      String content,
      String defaultNamespace,
      Result result,
      JsonMapper mapper) {
    final KubernetesObject parsed;
    try (final JsonMapper parseMapper = KubernetesActionUtil.createMapper()) {
      parsed = parseMapper.fromString(KubernetesObject.class, content);
    } catch (final Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesApply.Log.UnableToApply", "manifest", e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return;
    }
    String apiVersion = parsed == null ? null : parsed.apiVersion();
    String kind = parsed == null ? null : parsed.kind();
    if (apiVersion == null || kind == null) {
      logBasic(BaseMessages.getString(PKG, "ActionKubernetesApply.Log.SkipNoMetadata"));
      return;
    }
    String name = parsed.metadata() == null ? null : parsed.metadata().name();
    if (name == null) {
      logBasic(BaseMessages.getString(PKG, "ActionKubernetesApply.Log.SkipNoName"));
      return;
    }
    String metaNs = parsed.metadata().namespace();
    String ns = Utils.isEmpty(metaNs) ? defaultNamespace : metaNs;
    final KubernetesApiDiscovery discovery = new KubernetesApiDiscovery(client, mapper);
    final KubernetesComponentAwaiter awaiter =
        new KubernetesComponentAwaiter(discovery, client, mapper);
    String path = discovery.resourcePath(apiVersion, kind, ns, name);

    boolean exists;
    try {
      HttpRequest get =
          HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + path)).GET().build();
      HttpResponse<String> resp = client.send(KubernetesActionUtil.withJsonAccept(get));
      exists = resp.statusCode() == 200;
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesApply.Log.UnableToApply", name, e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return;
    }

    try {
      HttpResponse<String> resp =
          exists
              ? updateResource(client, discovery, apiVersion, kind, ns, name, content)
              : createResource(client, discovery, apiVersion, kind, ns, content);
      if (resp.statusCode() == 409) {
        // created concurrently, switch to update
        resp = updateResource(client, discovery, apiVersion, kind, ns, name, content);
      }
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        logError(
            BaseMessages.getString(
                PKG, "ActionKubernetesApply.Log.UnableToApply", name, resp.body()));
        result.setNrErrors(result.getNrErrors() + 1);
        result.setResult(false);
        return;
      }
      logDetailed(BaseMessages.getString(PKG, "ActionKubernetesApply.Log.FileApplied", name));
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesApply.Log.UnableToApply", name, e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return;
    }

    if (!waitReady) {
      return;
    }
    final long deadlineMillis = System.currentTimeMillis() + (long) rolloutTimeoutSeconds * 1000L;
    final boolean ready;
    if (awaiter.supports(kind)) {
      final KubernetesComponentAwaiter.Result awaitResult =
          awaiter.await(apiVersion, kind, ns, name, deadlineMillis, 2000);
      if (awaitResult == KubernetesComponentAwaiter.Result.FAILED) {
        logError(
            BaseMessages.getString(PKG, "ActionKubernetesApply.Log.ComponentFailed", name, kind));
        result.setNrErrors(result.getNrErrors() + 1);
        result.setResult(false);
        return;
      }
      ready = awaitResult == KubernetesComponentAwaiter.Result.READY;
    } else {
      final AwaitMeta awaited = findAwaitedMeta(kind);
      if (awaited == null) {
        logError(
            BaseMessages.getString(
                PKG, "ActionKubernetesApply.Log.NotReady", name, "[none]", "[none]"));
        result.setNrErrors(result.getNrErrors() + 1);
        result.setResult(false);
        return;
      }
      final HttpRequest getter =
          HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + path)).GET().build();
      logBasic(
          BaseMessages.getString(
              PKG,
              "ActionKubernetesApply.Log.Awaiting",
              name,
              awaited.field(),
              awaited.expected()));
      ready =
          KubernetesActionUtil.await(
              client,
              getter,
              awaited.field(),
              awaited.expected(),
              awaited.matches(),
              deadlineMillis,
              2000);
    }
    if (!ready) {
      logError(
          BaseMessages.getString(PKG, "ActionKubernetesApply.Log.ComponentNotReady", name, kind));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
    }
  }

  private HttpResponse<String> createResource(
      final KubernetesClient client,
      final KubernetesApiDiscovery discovery,
      final String apiVersion,
      final String kind,
      final String ns,
      final String content) {
    final String path = discovery.resourcePath(apiVersion, kind, ns, null);
    return client.send(
        KubernetesActionUtil.withJsonAccept(
            HttpRequest.newBuilder()
                .uri(URI.create("https://kubernetes.api" + path))
                .method("POST", HttpRequest.BodyPublishers.ofString(content))
                .header("Content-Type", "application/json")
                .build()));
  }

  private HttpResponse<String> updateResource(
      final KubernetesClient client,
      final KubernetesApiDiscovery discovery,
      final String apiVersion,
      final String kind,
      final String ns,
      final String name,
      final String content) {
    final String path = discovery.resourcePath(apiVersion, kind, ns, name);
    final UpdateMode mode = updateMode == null ? UpdateMode.PATCH : updateMode;
    final ApiPhase phase = mode == UpdateMode.PUT ? ApiPhase.REPLACE : ApiPhase.MERGE;
    HttpResponse<String> resp =
        client.send(
            KubernetesActionUtil.withJsonAccept(
                HttpRequest.newBuilder()
                    .uri(URI.create("https://kubernetes.api" + path))
                    .method(mode.verb(phase), HttpRequest.BodyPublishers.ofString(content))
                    .header("Content-Type", mode.contentType(phase))
                    .build()));
    if (mode == UpdateMode.PATCH && resp.statusCode() == 415) {
      // strategic merge patch rejected, fall back to merge patch (e.g. CRDs)
      resp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + path))
                      .method("PATCH", HttpRequest.BodyPublishers.ofString(content))
                      .header("Content-Type", "application/merge-patch+json")
                      .build()));
    }
    return resp;
  }

  /**
   * Resolve the (JSON pointer, expected value) couple used to wait for readiness. Explicit {@link
   * #awaitField}/{@link #awaitValue} win; otherwise a well-known default per kind is used
   * (Deployment/ReplicaSet/StatefulSet/Job). Returns {@code null} when nothing can be awaited so
   * the caller can fail instead of skipping silently.
   */
  private AwaitMeta findAwaitedMeta(final String kind) {
    final String field = resolve(awaitField);
    final String expected = resolve(awaitValue);
    if (!Utils.isEmpty(field) && !Utils.isEmpty(expected)) {
      return new AwaitMeta(field, expected, null);
    }
    if (!Utils.isEmpty(field)) {
      return new AwaitMeta(field, "true", null);
    }
    switch (kind == null ? "" : kind) {
      case "Deployment":
      case "ReplicaSet":
      case "StatefulSet":
      case "DaemonSet":
      case "ReplicationController":
        return AwaitMeta.readyReplicas();
      case "Job":
        return new AwaitMeta("/status/succeeded", "1", null);
      default:
        return null;
    }
  }

  /**
   * A readiness probe: a JSON pointer plus the exact expected value, or an optional body predicate.
   */
  private record AwaitMeta(String field, String expected, Predicate<Object> matches) {
    /** Ready once {@code status.readyReplicas >= status.replicas} (scale set readiness). */
    private static AwaitMeta readyReplicas() {
      return new AwaitMeta(
          "/status/readyReplicas",
          "[ready]",
          body -> {
            final Object status = body instanceof Map<?, ?> map ? map.get("status") : null;
            if (!(status instanceof Map<?, ?> s)) {
              return false;
            }
            final Object ready = s.get("readyReplicas");
            final Object replicas = s.get("replicas");
            if (!(ready instanceof Number r) || !(replicas instanceof Number n)) {
              return false;
            }
            return n.intValue() <= 0 || r.intValue() >= n.intValue();
          });
    }
  }
}
