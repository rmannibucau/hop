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

import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Scale a Kubernetes workload to a given number of replicas. */
@Action(
    id = "KUBERNETES_SCALE",
    name = "i18n::ActionKubernetesScale.Name",
    description = "i18n::ActionKubernetesScale.Description",
    image = "k8s-scale.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionKubernetesScale.keyword",
    documentationUrl = "/workflow/actions/kubernetes/kubernetes-scale.html")
@Getter
@Setter
public class ActionKubernetesScale extends ActionKubernetesBase {

  private static final Class<?> PKG = ActionKubernetesScale.class;

  @HopMetadataProperty(key = "kind")
  private String kind;

  @HopMetadataProperty(key = "name")
  private String name;

  @HopMetadataProperty(key = "replicas")
  private int replicas;

  @HopMetadataProperty(key = "wait_ready")
  private boolean waitReady;

  @HopMetadataProperty(key = "timeout_seconds")
  private int timeoutSeconds;

  public ActionKubernetesScale() {}

  public ActionKubernetesScale(final String name) {
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
            "ActionKubernetesScale");
    if (connection == null) {
      return result;
    }

    String rKind = resolve(kind);
    String rName = resolve(name);
    if (Utils.isEmpty(rKind) || Utils.isEmpty(rName)) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesScale.Error.NoNameOrKind"));
      result.setNrErrors(1);
      result.setResult(false);
      return result;
    }
    if (replicas < 0) {
      logError(
          BaseMessages.getString(PKG, "ActionKubernetesScale.Error.InvalidReplicas", replicas));
      result.setNrErrors(1);
      result.setResult(false);
      return result;
    }
    String apiVersion = "apps/v1";
    String ns = KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, null);
    int timeout = timeoutSeconds > 0 ? timeoutSeconds : 180;
    String path = KubernetesActionUtil.resourcePath(apiVersion, rKind, ns, rName);

    try (KubernetesClient client = KubernetesUtil.createClient(connection, this)) {
      String patch = "{\"spec\":{\"replicas\":" + replicas + "}}";
      HttpResponse<String> resp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + path))
                      .method("PATCH", HttpRequest.BodyPublishers.ofString(patch))
                      .header("Content-Type", "application/strategic-merge-patch+json")
                      .build()));
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        logError(
            BaseMessages.getString(
                PKG,
                "ActionKubernetesScale.Error.ScaleFailed",
                rName,
                resp.statusCode(),
                resp.body()));
        result.setNrErrors(result.getNrErrors() + 1);
        result.setResult(false);
        return result;
      }
      logBasic(
          BaseMessages.getString(PKG, "ActionKubernetesScale.Log.ScaleStarted", rName, replicas));

      if (waitReady) {
        if (!waitReadyState(client, path, replicas, timeout)) {
          result.setNrErrors(result.getNrErrors() + 1);
          result.setResult(false);
          return result;
        }
      }
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesScale.Error.ScaleFailedMsg", rName, e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return result;
    }
    if (result.getNrErrors() == 0) {
      result.setResult(true);
      KubernetesActionUtil.expose(result, true, ns, rName);
    }
    return result;
  }

  private boolean waitReadyState(KubernetesClient client, String path, int replicas, int timeout) {
    final HttpRequest getter =
        HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + path)).GET().build();
    final boolean ready =
        KubernetesActionUtil.await(
            client,
            getter,
            "/status/readyReplicas",
            null,
            body -> {
              final Object status = body instanceof Map<?, ?> m ? m.get("status") : null;
              if (!(status instanceof Map<?, ?> s)) {
                return false;
              }
              final Object readyVal = s.get("readyReplicas");
              if (!(readyVal instanceof Number r)) {
                return false;
              }
              return r.intValue() >= replicas;
            },
            System.currentTimeMillis() + timeout * 1000L,
            2000);
    if (ready) {
      logBasic(BaseMessages.getString(PKG, "ActionKubernetesScale.Log.ScaleReady", replicas));
    } else {
      logError(
          BaseMessages.getString(PKG, "ActionKubernetesScale.Log.ScaleTimeout", replicas, timeout));
    }
    return ready;
  }
}
