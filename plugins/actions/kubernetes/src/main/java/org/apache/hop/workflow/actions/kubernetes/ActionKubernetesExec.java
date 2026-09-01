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
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Execute a command in a Kubernetes pod (best effort; streams pod logs). */
@Action(
    id = "KUBERNETES_EXEC",
    name = "i18n::ActionKubernetesExec.Name",
    description = "i18n::ActionKubernetesExec.Description",
    image = "k8s-exec.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionKubernetesExec.keyword",
    documentationUrl = "/workflow/actions/kubernetes/kubernetes-exec.html")
@Getter
@Setter
public class ActionKubernetesExec extends ActionKubernetesBase {

  private static final Class<?> PKG = ActionKubernetesExec.class;

  @HopMetadataProperty(key = "pod")
  private String podName;

  @HopMetadataProperty(key = "container")
  private String containerName;

  @HopMetadataProperty(key = "command")
  private String command;

  @HopMetadataProperty(key = "wait_timeout_seconds")
  private int waitTimeoutSeconds;

  public ActionKubernetesExec() {}

  public ActionKubernetesExec(final String name) {
    this();
    setName(name);
  }

  @Override
  public Result execute(Result previousResult, int nr) throws HopException {
    Result result = previousResult;
    result.setResult(false);

    final KubernetesConnection connection =
        KubernetesActionUtil.resolveConnection(
            connectionName, getMetadataProvider(), getLogChannel(), result, "ActionKubernetesExec");
    if (connection == null) {
      return result;
    }

    String pod = resolve(podName);
    if (Utils.isEmpty(pod)) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesExec.Error.NoPod"));
      result.setNrErrors(1);
      result.setResult(false);
      return result;
    }
    String ns = KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, null);
    String container = resolve(containerName);

    try (KubernetesClient client = KubernetesUtil.createClient(connection, this)) {
      String podPath = KubernetesActionUtil.resourcePath("v1", "Pod", ns, pod);
      HttpResponse<String> podResp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + podPath))
                      .GET()
                      .build()));
      if (podResp.statusCode() < 200 || podResp.statusCode() >= 300) {
        logError(
            BaseMessages.getString(
                PKG,
                "ActionKubernetesExec.Error.PodNotFound",
                pod,
                nr,
                podResp.statusCode(),
                podResp.body()));
        result.setNrErrors(result.getNrErrors() + 1);
        result.setResult(false);
        return result;
      }
      if (Utils.isEmpty(container)) {
        container = findFirstContainerName(podResp.body());
        if (Utils.isEmpty(container)) {
          container = "main";
        }
      }
      logBasic(
          BaseMessages.getString(PKG, "ActionKubernetesExec.Log.ContainerResolved", container));

      String cmd = resolve(command);
      if (!Utils.isEmpty(cmd)) {
        // The fusion HTTP client does not expose an interactive exec channel, log the limitation.
        logError(
            BaseMessages.getString(
                PKG, "ActionKubernetesExec.Log.ExecUnsupported", pod, container, cmd));
        result.setNrErrors(1);
        result.setResult(false);
        return result;
      }

      // Fallback: observe the pod logs as the actionable output.
      String logPath =
          podPath + "/log?container=" + KubernetesActionUtil.encode(container) + "&follow=true";
      HttpResponse<String> logResp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + logPath))
                      .GET()
                      .build()));
      if (logResp.statusCode() < 200 || logResp.statusCode() >= 300) {
        logMinimal(
            BaseMessages.getString(
                PKG,
                "ActionKubernetesExec.Log.FetchLogsFailed",
                pod,
                podResp.statusCode(),
                logResp.body()));
      } else {
        logBasic(BaseMessages.getString(PKG, "ActionKubernetesExec.Log.PodLogs", pod));
        logBasic(logResp.body());
      }
      result.setResult(true);
      result.setLogText(
          BaseMessages.getString(PKG, "ActionKubernetesExec.Log.Executed", pod, container));
      KubernetesActionUtil.expose(result, true, ns, pod);
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesExec.Error.ExecFailed", pod, e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return result;
    }
    return result;
  }

  private String findFirstContainerName(String podBody) {
    try (final JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      final Object root = mapper.fromString(Object.class, podBody);
      final Object container = new GenericJsonPointer("/spec/containers/0/name").apply(root);
      return container == null ? null : String.valueOf(container);
    } catch (final Exception e) {
      logError("Unable to parse pod to locate a container", e);
      return null;
    }
  }
}
