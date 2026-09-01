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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.kubernetes.model.KubernetesObject;
import org.apache.hop.kubernetes.model.KubernetesObjectList;
import org.apache.hop.kubernetes.util.KubernetesApiDiscovery;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Delete one or several Kubernetes resources from a cluster. */
@Action(
    id = "KUBERNETES_DELETE",
    name = "i18n::ActionKubernetesDelete.Name",
    description = "i18n::ActionKubernetesDelete.Description",
    image = "k8s-delete.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionKubernetesDelete.keyword",
    documentationUrl = "/workflow/actions/kubernetes/kubernetes-delete.html")
@Getter
@Setter
public class ActionKubernetesDelete extends ActionKubernetesBase {

  private static final Class<?> PKG = ActionKubernetesDelete.class;

  @HopMetadataProperty(key = "kind")
  private String kind;

  @HopMetadataProperty(key = "name")
  private String name;

  @HopMetadataProperty(key = "grace_period_seconds")
  private int gracePeriodSeconds;

  @HopMetadataProperty(key = "ignore_not_found")
  private boolean ignoreNotFound;

  public ActionKubernetesDelete() {}

  public ActionKubernetesDelete(final String name) {
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
            "ActionKubernetesDelete");
    if (connection == null) {
      return result;
    }

    String rKind = resolve(kind);
    if (Utils.isEmpty(rKind)) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesDelete.Error.NoKind"));
      result.setNrErrors(1);
      return result;
    }
    String ns = KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, null);
    String rName = resolve(name);
    String rLabelSelector = resolve(labelSelector);
    int grace = gracePeriodSeconds > 0 ? gracePeriodSeconds : 30;

    try (KubernetesClient client = KubernetesUtil.createClient(connection, this);
        JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      final KubernetesApiDiscovery discovery = new KubernetesApiDiscovery(client, mapper);
      final String apiVersion = discovery.apiVersionOrGuess(rKind);
      if (!Utils.isEmpty(rName)) {
        deleteOne(client, discovery, apiVersion, rKind, ns, rName, grace, result);
      } else if (!Utils.isEmpty(rLabelSelector)) {
        String listPath =
            KubernetesActionUtil.resourcePath(apiVersion, rKind, ns, null)
                + "?labelSelector="
                + KubernetesActionUtil.encode(rLabelSelector);
        HttpResponse<String> list =
            client.send(
                KubernetesActionUtil.withJsonAccept(
                    HttpRequest.newBuilder()
                        .uri(URI.create("https://kubernetes.api" + listPath))
                        .GET()
                        .build()));
        if (list.statusCode() < 200 || list.statusCode() >= 300) {
          logError(
              BaseMessages.getString(
                  PKG, "ActionKubernetesDelete.Error.ListFailed", list.statusCode(), list.body()));
          result.setNrErrors(result.getNrErrors() + 1);
          result.setResult(false);
          return result;
        }
        List<String> names = extractItemNames(list.body());
        if (names.isEmpty()) {
          logBasic(
              BaseMessages.getString(PKG, "ActionKubernetesDelete.Log.NoMatches", rLabelSelector));
        }
        for (String item : names) {
          deleteOne(client, discovery, apiVersion, rKind, ns, item, grace, result);
        }
      } else {
        logError(BaseMessages.getString(PKG, "ActionKubernetesDelete.Error.NoNameOrSelector"));
        result.setNrErrors(1);
        result.setResult(false);
        return result;
      }
    } catch (Exception e) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesDelete.Error.DeleteFailed"), e);
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return result;
    }

    if (result.getNrErrors() == 0) {
      result.setResult(true);
      KubernetesActionUtil.expose(
          result,
          true,
          KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, null),
          resolve(name));
    }
    return result;
  }

  private void deleteOne(
      KubernetesClient client,
      KubernetesApiDiscovery discovery,
      String apiVersion,
      String kind,
      String ns,
      String name,
      int grace,
      Result result) {
    String path = discovery.resourcePath(apiVersion, kind, ns, name);
    try {
      HttpResponse<String> resp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + path))
                      .method(
                          "DELETE",
                          HttpRequest.BodyPublishers.ofString(
                              "{\"gracePeriodSeconds\":" + grace + "}"))
                      .header("Content-Type", "application/json")
                      .build()));
      if (resp.statusCode() == 404 && ignoreNotFound) {
        logBasic(BaseMessages.getString(PKG, "ActionKubernetesDelete.Log.NotFoundIgnored", name));
        return;
      }
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        logError(
            BaseMessages.getString(
                PKG,
                "ActionKubernetesDelete.Error.DeleteFailedStatus",
                name,
                resp.statusCode(),
                resp.body()));
        result.setNrErrors(result.getNrErrors() + 1);
        result.setResult(false);
        return;
      }
      logDetailed(BaseMessages.getString(PKG, "ActionKubernetesDelete.Log.Deleted", name));
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesDelete.Error.DeleteFailedItem", name, e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
    }
  }

  private List<String> extractItemNames(String body) {
    List<String> names = new ArrayList<>();
    if (body == null) {
      return names;
    }
    try (final JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      final KubernetesObjectList list = mapper.fromString(KubernetesObjectList.class, body);
      if (list != null && list.items() != null) {
        for (final KubernetesObject item : list.items()) {
          if (item != null && item.metadata() != null && !Utils.isEmpty(item.metadata().name())) {
            names.add(item.metadata().name());
          }
        }
      }
    }
    return names;
  }
}
