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
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Result;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.kubernetes.model.KubernetesContainer;
import org.apache.hop.kubernetes.model.KubernetesJob;
import org.apache.hop.kubernetes.model.KubernetesJobSpec;
import org.apache.hop.kubernetes.model.KubernetesObject;
import org.apache.hop.kubernetes.model.KubernetesPodSpec;
import org.apache.hop.kubernetes.model.KubernetesPodTemplate;
import org.apache.hop.kubernetes.model.KubernetesStatus;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Run a Kubernetes Job workload. */
@Action(
    id = "KUBERNETES_RUN",
    name = "i18n::ActionKubernetesRun.Name",
    description = "i18n::ActionKubernetesRun.Description",
    image = "k8s-run.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.General",
    keywords = "i18n::ActionKubernetesRun.keyword",
    documentationUrl = "/workflow/actions/kubernetes/kubernetes-run.html")
@Getter
@Setter
public class ActionKubernetesRun extends ActionKubernetesBase {

  private static final Class<?> PKG = ActionKubernetesRun.class;

  @HopMetadataProperty(key = "image")
  private String image;

  @HopMetadataProperty(key = "command")
  private String command;

  @HopMetadataProperty(key = "args")
  private String args;

  @HopMetadataProperty(key = "name")
  private String name;

  @HopMetadataProperty(key = "job_name")
  private String jobName;

  @HopMetadataProperty(key = "completions")
  private int completions;

  @HopMetadataProperty(key = "parallelism")
  private int parallelism;

  @HopMetadataProperty(key = "backoff_limit")
  private int backoffLimit;

  @HopMetadataProperty(key = "ttl_seconds")
  private int ttlSeconds;

  @HopMetadataProperty(key = "active_deadline_seconds")
  private int activeDeadlineSeconds;

  @HopMetadataProperty(key = "wait_for_completion")
  private boolean waitForCompletion;

  @HopMetadataProperty(key = "delete_on_success")
  private boolean deleteOnSuccess;

  @HopMetadataProperty(key = "timeout_seconds")
  private int timeoutSeconds;

  @HopMetadataProperty(key = "await_field")
  private String awaitField;

  @HopMetadataProperty(key = "await_value")
  private String awaitValue;

  public ActionKubernetesRun() {}

  public ActionKubernetesRun(final String name) {
    this();
    setName(name);
  }

  @Override
  public Result execute(Result previousResult, int nr) throws HopException {
    Result result = previousResult;
    result.setResult(false);

    final KubernetesConnection connection =
        KubernetesActionUtil.resolveConnection(
            connectionName, getMetadataProvider(), getLogChannel(), result, "ActionKubernetesRun");
    if (connection == null) {
      return result;
    }

    String ns = KubernetesActionUtil.resolveNamespace(resolve(namespace), connection, null);
    String jobName = resolve(this.jobName);
    if (Utils.isEmpty(jobName)) {
      String rName = resolve(name);
      if (Utils.isEmpty(rName)) {
        jobName = "hop-job-" + Long.toHexString(System.currentTimeMillis());
      } else {
        jobName = rName.toLowerCase().replace(' ', '-');
      }
    }
    String image = resolve(this.image);
    if (Utils.isEmpty(image)) {
      logError(BaseMessages.getString(PKG, "ActionKubernetesRun.Error.NoImage"));
      result.setNrErrors(1);
      result.setResult(false);
      return result;
    }
    List<String> cmd = splitTokens(resolve(command));
    List<String> args = splitTokens(resolve(this.args));

    final KubernetesContainer container =
        new KubernetesContainer(
            "main", image, cmd.isEmpty() ? null : cmd, args.isEmpty() ? null : args);
    final KubernetesJob job =
        KubernetesJob.of(
            jobName,
            ns,
            new KubernetesJobSpec(
                completions > 0 ? completions : null,
                parallelism > 0 ? parallelism : null,
                backoffLimit >= 0 ? backoffLimit : null,
                ttlSeconds > 0 ? ttlSeconds : null,
                activeDeadlineSeconds > 0 ? activeDeadlineSeconds : null,
                new KubernetesPodTemplate(new KubernetesPodSpec("Never", List.of(container)))));

    final String manifest;
    try (final JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      manifest = mapper.toString(job);
    }

    String path = KubernetesActionUtil.resourcePath("batch/v1", "Job", ns, null);
    try (KubernetesClient client = KubernetesUtil.createClient(connection, this)) {
      HttpResponse<String> resp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + path))
                      .method("POST", HttpRequest.BodyPublishers.ofString(manifest))
                      .header("Content-Type", "application/json")
                      .build()));
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        logError(
            BaseMessages.getString(
                PKG,
                "ActionKubernetesRun.Error.CreateFailed",
                jobName,
                resp.statusCode(),
                resp.body()));
        result.setNrErrors(result.getNrErrors() + 1);
        result.setResult(false);
        return result;
      }
      logBasic(BaseMessages.getString(PKG, "ActionKubernetesRun.Log.JobCreated", jobName));

      if (waitForCompletion) {
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : 3600;
        boolean succeeded;
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
                  PKG, "ActionKubernetesRun.Log.Awaiting", jobName, pointerField, expectedValue));
          succeeded =
              KubernetesActionUtil.await(
                  client,
                  getter,
                  pointerField,
                  expectedValue,
                  System.currentTimeMillis() + (long) timeout * 1000L,
                  2000);
          if (!succeeded) {
            logError(
                BaseMessages.getString(
                    PKG, "ActionKubernetesRun.Log.NotReady", jobName, pointerField, expectedValue));
          }
        } else {
          succeeded =
              waitForCompletion(client, ns, jobName, completions > 0 ? completions : 1, timeout);
        }
        if (!succeeded) {
          result.setNrErrors(result.getNrErrors() + 1);
          result.setResult(false);
        } else {
          result.setResult(true);
          KubernetesActionUtil.expose(result, true, ns, jobName);
        }
        if (deleteOnSuccess) {
          deleteJob(client, ns, jobName, result);
        }
      } else {
        result.setResult(true);
        KubernetesActionUtil.expose(result, true, ns, jobName);
      }
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesRun.Error.RunFailed", jobName, e.getMessage()));
      result.setNrErrors(result.getNrErrors() + 1);
      result.setResult(false);
      return result;
    }
    return result;
  }

  private boolean waitForCompletion(
      KubernetesClient client, String ns, String jobName, int expected, int timeout) {
    String path = KubernetesActionUtil.resourcePath("batch/v1", "Job", ns, jobName);
    long start = System.currentTimeMillis();
    try (final JsonMapper mapper = KubernetesActionUtil.createMapper()) {
      final HttpRequest getter =
          KubernetesActionUtil.withJsonAccept(
              HttpRequest.newBuilder()
                  .uri(URI.create("https://kubernetes.api" + path))
                  .GET()
                  .build());
      while (System.currentTimeMillis() - start < timeout * 1000L) {
        try {
          HttpResponse<String> resp = client.send(getter);
          if (resp.statusCode() == 200) {
            final KubernetesStatus status =
                mapper.fromString(KubernetesObject.class, resp.body()).status();
            int failed = status == null ? 0 : status.failedOrDefault();
            if (failed > 0) {
              logError(
                  BaseMessages.getString(
                      PKG, "ActionKubernetesRun.Log.JobFailed", jobName, failed));
              return false;
            }
            int succeeded = status.succeededOrDefault();
            if (succeeded >= expected) {
              logBasic(
                  BaseMessages.getString(
                      PKG, "ActionKubernetesRun.Log.JobSucceeded", jobName, succeeded));
              return true;
            }
          }
        } catch (Exception e) {
          // ignore transient errors, keep polling
        }
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    logError(BaseMessages.getString(PKG, "ActionKubernetesRun.Log.JobTimedOut", jobName, timeout));
    return false;
  }

  private void deleteJob(KubernetesClient client, String ns, String jobName, Result result) {
    String path = KubernetesActionUtil.resourcePath("batch/v1", "Job", ns, jobName);
    try {
      HttpResponse<String> resp =
          client.send(
              KubernetesActionUtil.withJsonAccept(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + path))
                      .method(
                          "DELETE",
                          HttpRequest.BodyPublishers.ofString("{\"gracePeriodSeconds\":0}"))
                      .header("Content-Type", "application/json")
                      .build()));
      logBasic(BaseMessages.getString(PKG, "ActionKubernetesRun.Log.JobDeleted", jobName));
    } catch (Exception e) {
      logError(
          BaseMessages.getString(
              PKG, "ActionKubernetesRun.Log.JobDeleteFailed", jobName, e.getMessage()));
    }
  }

  private java.util.List<String> splitTokens(String value) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (Utils.isEmpty(value)) {
      return out;
    }
    for (String token : value.split("\\s+")) {
      if (!token.isEmpty()) {
        out.add(token);
      }
    }
    return out;
  }
}
