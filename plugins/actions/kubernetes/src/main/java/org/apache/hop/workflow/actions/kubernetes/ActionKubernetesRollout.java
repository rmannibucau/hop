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
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.kubernetes.model.KubernetesCondition;
import org.apache.hop.kubernetes.model.KubernetesObject;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Trigger a rollout operation on a Kubernetes workload. */
@Action(
    id = "KUBERNETES_ROLLOUT",
    name = "i18n::ActionKubernetesRollout.Name",
    description = "i18n::ActionKubernetesRollout.Description",
    image = "k8s-rollout.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionKubernetesRollout.keyword",
    documentationUrl = "/workflow/actions/kubernetes/kubernetes-rollout.html")
@Getter
@Setter
public class ActionKubernetesRollout extends ActionKubernetesBase {

  private static final Class<?> PKG = ActionKubernetesRollout.class;

  @HopMetadataProperty(key = "kind")
  private String kind;

  @HopMetadataProperty(key = "name")
  private String name;

  @HopMetadataProperty(key = "operation")
  private String operation;

  @HopMetadataProperty(key = "desired_status")
  private String desiredStatus;

  @HopMetadataProperty(key = "timeout_seconds")
  private int timeoutSeconds;

  @HopMetadataProperty(key = "await_field")
  private String awaitField;

  @HopMetadataProperty(key = "await_value")
  private String awaitValue;

  public ActionKubernetesRollout() {}

  public ActionKubernetesRollout(final String name) {
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
            "ActionKubernetesRollout");
    if (connection == null) {
      return result;
    }

    String rKind = resolve(kind);
    String rName = resolve(name);
    if (Utils.isEmpty(rKind) || (Utils.isEmpty(rName) && Utils.isEmpty(labelSelector))) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesRollout.Error.NoNameOrKind"));
      result.setNrErrors(1);
      result.setResult(false);
      return result;
    }
    String apiVersion = "apps/v1";
    String ns = KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, null);
    String op = resolve(operation);
    if (Utils.isEmpty(op)) {
      op = "restart";
    }
    String desired = resolve(desiredStatus);
    if (Utils.isEmpty(desired)) {
      desired = "Available";
    }
    int timeout = timeoutSeconds > 0 ? timeoutSeconds : 180;

    String target = null;
    try (KubernetesClient client = KubernetesUtil.createClient(connection, this);
        JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      target = getTargetName(client, apiVersion, rKind, ns, rName, mapper);
      String path = KubernetesActionUtil.resourcePath(apiVersion, rKind, ns, target);
      switch (op) {
        case "restart":
          doRestart(client, path, target, result);
          break;
        case "status":
          doStatus(client, path, result);
          break;
        case "wait":
          boolean ready;
          if (!Utils.isEmpty(awaitField) && !Utils.isEmpty(awaitValue)) {
            String pointerField = resolve(awaitField);
            String expectedValue = resolve(awaitValue);
            HttpRequest getter =
                HttpRequest.newBuilder()
                    .uri(URI.create("https://kubernetes.api" + path))
                    .GET()
                    .build();
            logBasic(
                BaseMessages.getString(
                    PKG,
                    "ActionKubernetesRollout.Log.Awaiting",
                    target,
                    pointerField,
                    expectedValue));
            ready =
                KubernetesActionUtil.await(
                    client,
                    getter,
                    pointerField,
                    expectedValue,
                    System.currentTimeMillis() + (long) timeout * 1000L,
                    2000);
            if (!ready) {
              logError(
                  BaseMessages.getString(
                      PKG,
                      "ActionKubernetesRollout.Log.NotReady",
                      target,
                      pointerField,
                      expectedValue));
            }
          } else {
            ready = doWait(client, path, desired, timeout, target);
          }
          if (!ready) {
            result.setNrErrors(result.getNrErrors() + 1);
            result.setResult(false);
            return result;
          }
          result.setResult(true);
          break;
        default:
          logError(
              BaseMessages.getString(PKG, "ActionKubernetesRollout.Error.UnknownOperation", op));
          result.setNrErrors(1);
          result.setResult(false);
          return result;
      }
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesRollout.Error.RolloutFailed", target, op, e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return result;
    }
    if (result.getNrErrors() == 0) {
      result.setResult(true);
      KubernetesActionUtil.expose(result, true, ns, target);
    }
    return result;
  }

  private void doRestart(KubernetesClient client, String path, String target, Result result)
      throws Exception {
    String patch =
        "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"kubectl.kubernetes.io/restartedAt\":\""
            + Instant.now()
            + "\"}}}}}";
    HttpResponse<String> resp =
        client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("https://kubernetes.api" + path))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(patch))
                .header("Content-Type", "application/strategic-merge-patch+json")
                .build());
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      logError(
          BaseMessages.getString(
              PKG,
              "ActionKubernetesRollout.Error.RestartFailed",
              target,
              resp.statusCode(),
              resp.body()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return;
    }
    logBasic(BaseMessages.getString(PKG, "ActionKubernetesRollout.Log.Restarted", target));
  }

  private void doStatus(KubernetesClient client, String path, Result result) throws Exception {
    HttpResponse<String> resp =
        client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("https://kubernetes.api" + path))
                .GET()
                .build());
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesRollout.Error.StatusFailed", resp.statusCode(), resp.body()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return;
    }
    String body = resp.body();
    int observed = observedGeneration(body);
    logBasic(BaseMessages.getString(PKG, "ActionKubernetesRollout.Log.Status", observed));
  }

  private int observedGeneration(final String body) {
    try (final JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      final KubernetesObject obj = mapper.fromString(KubernetesObject.class, body);
      return obj.status() == null ? -1 : obj.status().observedOrDefault();
    } catch (final RuntimeException e) {
      return -1;
    }
  }

  private String getTargetName(
      final KubernetesClient client,
      final String apiVersion,
      final String kind,
      final String ns,
      final String name,
      final JsonMapper mapper)
      throws Exception {
    return KubernetesActionUtil.resolveResourceName(
        client, apiVersion, kind, ns, name, labelSelector, mapper);
  }

  private boolean doWait(
      KubernetesClient client, String path, String desired, int timeout, String rName)
      throws Exception {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeout * 1000L) {
      HttpResponse<String> resp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + path))
                      .GET()
                      .build()));
      if (resp.statusCode() == 200) {
        String body = resp.body();
        if (conditionMet(body, desired)) {
          logBasic(
              BaseMessages.getString(
                  PKG, "ActionKubernetesRollout.Log.ConditionMet", rName, desired));
          return true;
        }
      }
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    logError(
        BaseMessages.getString(
            PKG, "ActionKubernetesRollout.Log.ConditionNotMet", rName, desired, timeout));
    return false;
  }

  private boolean conditionMet(String body, String desired) {
    try (final JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      final KubernetesObject obj = mapper.fromString(KubernetesObject.class, body);
      if (obj.status() == null || obj.status().conditions() == null) {
        return false;
      }
      for (final KubernetesCondition condition : obj.status().conditions()) {
        if (condition != null
            && desired.equals(condition.type())
            && "True".equals(condition.status())) {
          return true;
        }
      }
    } catch (final RuntimeException e) {
      // malformed body, treat as not met
    }
    return false;
  }
}
