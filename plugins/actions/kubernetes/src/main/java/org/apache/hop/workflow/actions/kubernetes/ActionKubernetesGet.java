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

package org.apache.hop.workflow.actions.kubernetes;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.apache.hop.kubernetes.util.KubernetesUtil;

/**
 * Fetch a single Kubernetes resource by name or by label selector and expose its JSON document.
 * When a label selector is used the result must match exactly one resource, otherwise the action
 * fails.
 */
@Action(
    id = "KUBERNETES_GET",
    name = "i18n::ActionKubernetesGet.Name",
    description = "i18n::ActionKubernetesGet.Description",
    image = "k8s-get.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionKubernetesGet.keyword",
    documentationUrl = "/workflow/actions/kubernetes/kubernetes-get.html")
@Getter
@Setter
public class ActionKubernetesGet extends ActionKubernetesBase {

  private static final Class<?> PKG = ActionKubernetesGet.class;

  @org.apache.hop.metadata.api.HopMetadataProperty(key = "kind")
  private String kind;

  /** Optional explicit apiVersion; when empty the connection/discovery default is used. */
  @org.apache.hop.metadata.api.HopMetadataProperty(key = "api_version")
  private String apiVersion;

  @org.apache.hop.metadata.api.HopMetadataProperty(key = "name")
  private String name;

  public ActionKubernetesGet() {}

  public ActionKubernetesGet(final String name) {
    this();
    setName(name);
  }

  @Override
  public Result execute(Result previousResult, int nr) throws HopException {
    Result result = previousResult;
    result.setResult(false);

    final KubernetesConnection connection =
        KubernetesActionUtil.resolveConnection(
            connectionName, getMetadataProvider(), getLogChannel(), result, "ActionKubernetesGet");
    if (connection == null) {
      return result;
    }
    final String rKind = resolve(kind);
    final String rName = resolve(name);
    final String rLabel = resolve(labelSelector);
    if (Utils.isEmpty(rKind)) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesGet.Error.NoKind"));
      result.setNrErrors(result.getNrErrors() + 1);
      return result;
    }
    if (Utils.isEmpty(rName) && Utils.isEmpty(rLabel)) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesGet.Error.NoNameOrSelector"));
      result.setNrErrors(result.getNrErrors() + 1);
      return result;
    }
    final String ns = KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, null);

    try (KubernetesClient client = KubernetesUtil.createClient(connection, this);
        JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      final KubernetesApiDiscovery discovery = new KubernetesApiDiscovery(client, mapper);
      final String api =
          !Utils.isEmpty(apiVersion) ? resolve(apiVersion) : discovery.apiVersionOrGuess(rKind);

      if (!Utils.isEmpty(rName)) {
        final String path = discovery.resourcePath(api, rKind, ns, rName);
        final HttpResponse<String> resp = doGet(client, path);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
          logError(
              BaseMessages.getString(
                  PKG, "ActionKubernetesGet.Error.Failed", rKind, rName, resp.statusCode()));
          result.setNrErrors(result.getNrErrors() + 1);
          return result;
        }
        final String body = resp.body();
        final KubernetesObject obj = mapper.fromString(KubernetesObject.class, body);
        if (obj == null || obj.metadata() == null) {
          logError(BaseMessages.getString(PKG, "ActionKubernetesGet.Error.NoObject", rKind, rName));
          result.setNrErrors(result.getNrErrors() + 1);
          return result;
        }
        expose(result, body);
        logDetailed(BaseMessages.getString(PKG, "ActionKubernetesGet.Log.Found", rKind, rName, ns));
        result.setResult(true);
        return result;
      }

      // label selector path: exactly one matching resource must exist
      final String listPath =
          discovery.resourcePath(api, rKind, ns, null)
              + "?labelSelector="
              + KubernetesActionUtil.encode(rLabel);
      final HttpResponse<String> list = doGet(client, listPath);
      if (list.statusCode() < 200 || list.statusCode() >= 300) {
        logError(
            BaseMessages.getString(
                PKG, "ActionKubernetesGet.Error.Failed", rKind, rLabel, list.statusCode()));
        result.setNrErrors(result.getNrErrors() + 1);
        return result;
      }
      final org.apache.hop.kubernetes.model.KubernetesObjectList parsed =
          mapper.fromString(
              org.apache.hop.kubernetes.model.KubernetesObjectList.class, list.body());
      final int size = parsed == null || parsed.items() == null ? 0 : parsed.items().size();
      if (size != 1) {
        logError(
            BaseMessages.getString(PKG, "ActionKubernetesGet.Error.NotOne", rKind, rLabel, size));
        result.setNrErrors(result.getNrErrors() + 1);
        return result;
      }
      final KubernetesObject item = parsed.items().get(0);
      final String body = mapper.toString(item);
      expose(result, body);
      result.setResult(true);
      return result;
    } catch (final Exception e) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesGet.Error.Generic", e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      return result;
    }
  }

  private HttpResponse<String> doGet(final KubernetesClient client, final String path) {
    return client.send(
        KubernetesActionUtil.withJsonAccept(
            HttpRequest.newBuilder()
                .uri(URI.create("https://kubernetes.api" + path))
                .GET()
                .build()));
  }

  /** Store the fetched descriptor so downstream steps can read it from the workflow result text. */
  private void expose(final Result result, final String body) {
    result.setNrLinesRead(1);
    final String existing = result.getLogText() == null ? "" : result.getLogText() + "\n";
    result.setLogText(existing + body);
    logBasic(BaseMessages.getString(PKG, "ActionKubernetesGet.Log.Body", body));
  }
}
