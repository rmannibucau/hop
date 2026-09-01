/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.kubernetes.apply;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.model.KubernetesObject;
import org.apache.hop.kubernetes.model.KubernetesObjectMetadata;
import org.apache.hop.kubernetes.util.KubernetesComponentAwaiter;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesApiService;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesOperationResult;

public class KubernetesApply extends BaseTransform<KubernetesApplyMeta, KubernetesApplyData> {

  private static final Class<?> PKG = KubernetesApiService.class;

  private int manifestFieldIndex = -1;

  public KubernetesApply(
      final TransformMeta transformMeta,
      final KubernetesApplyMeta meta,
      final KubernetesApplyData data,
      final int copyNr,
      final PipelineMeta pipelineMeta,
      final Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    final Object[] r = getRow();
    if (first) {
      first = false;
      prepareOutput();
      if (getInputRowMeta() == null) {
        throw new HopException("KubernetesApply requires an incoming stream");
      }
      manifestFieldIndex = getInputRowMeta().indexOfValue(meta.getManifestFieldName());
      if (manifestFieldIndex < 0) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "KubernetesApply.Error.NoManifestField", meta.getManifestFieldName()));
      }
    }
    if (r != null) {
      final String manifest = data.outputRowMeta.getString(r, manifestFieldIndex);
      if (manifest == null || manifest.trim().isEmpty()) {
        KubernetesApiService.failRow(
            this,
            r,
            BaseMessages.getString(
                PKG, "KubernetesApply.Error.NoManifestField", meta.getManifestFieldName()));
        return true;
      }
      final KubernetesOperationResult applied;
      try {
        applied = apply(manifest);
      } catch (final HopException e) {
        KubernetesApiService.failRow(this, r, e.getMessage());
        return true;
      }
      final Object[] outputRow = new Object[data.outputRowMeta.size()];
      outputRow[0] = applied.ok();
      outputRow[1] = applied.namespace();
      outputRow[2] = applied.name();
      putRow(data.outputRowMeta, outputRow);
      incrementLinesOutput();
      return true;
    }
    setOutputDone();
    return false;
  }

  @Override
  public boolean init() {
    if (super.init()) {
      data.service =
          KubernetesApiService.fromConnection(
              meta.getConnectionName(),
              getMetadataProvider(),
              variables,
              getLogChannel(),
              "KubernetesApply");
      return data.service != null;
    }
    return false;
  }

  @Override
  public void dispose() {
    KubernetesApiService.close(data.service);
    data.service = null;
    super.dispose();
  }

  private void prepareOutput() {
    data.outputRowMeta = getInputRowMeta().clone();
    meta.getFields(
        data.outputRowMeta, getTransformName(), null, null, variables, getMetadataProvider());
  }

  private KubernetesOperationResult apply(final String manifest) throws HopException {
    final KubernetesObject object;
    try {
      object = data.service.parseObject(KubernetesObject.class, manifest);
    } catch (final Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesApply.Error.InvalidManifest", e.getMessage()), e);
    }
    final KubernetesObjectMetadata metadata = object == null ? null : object.metadata();
    final String kind = object == null ? null : object.kind();
    final String bareName = metadata == null ? null : metadata.name();
    String resolvedNamespace = resolveVar(meta.getNamespace());
    if (Utils.isEmpty(resolvedNamespace)) {
      resolvedNamespace = metadata == null ? null : metadata.namespace();
    }
    final String namespace = resolvedNamespace;
    if (Utils.isEmpty(kind) || Utils.isEmpty(bareName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "KubernetesApply.Error.InvalidManifest", "missing kind or name"));
    }
    final String createPath = data.service.resourcePath(object.apiVersion(), kind, namespace, null);
    final String resourcePath =
        data.service.resourcePath(object.apiVersion(), kind, namespace, bareName);
    logBasic(
        BaseMessages.getString(PKG, "KubernetesApply.Log.Applying", kind, bareName, namespace));
    try {
      final HttpResponse<String> createResponse =
          data.service
              .client()
              .send(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://kubernetes.api" + createPath))
                      .method("POST", HttpRequest.BodyPublishers.ofString(manifest))
                      .header("Content-Type", "application/json")
                      .build());
      final int createStatus = createResponse.statusCode();
      if (createStatus == 201 || createStatus == 200) {
        maybeWaitReady(object.apiVersion(), kind, namespace, bareName);
        return KubernetesOperationResult.of(true, namespace, bareName);
      }
      if (createStatus == 409) {
        final HttpResponse<String> updateResponse =
            data.service
                .client()
                .send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("https://kubernetes.api" + resourcePath))
                        .method("PUT", HttpRequest.BodyPublishers.ofString(manifest))
                        .header("Content-Type", "application/json")
                        .build());
        if (updateResponse.statusCode() < 200 || updateResponse.statusCode() >= 300) {
          throw new HopException(
              "Kubernetes update failed with HTTP "
                  + updateResponse.statusCode()
                  + ": "
                  + updateResponse.body());
        }
        maybeWaitReady(object.apiVersion(), kind, namespace, bareName);
        return KubernetesOperationResult.of(true, namespace, bareName);
      }
      throw new HopException(
          "Kubernetes create failed with HTTP " + createStatus + ": " + createResponse.body());
    } catch (final HopException e) {
      throw e;
    } catch (final Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesApply.Error.ApplyFailed", e.getMessage()), e);
    }
  }

  private void maybeWaitReady(
      final String apiVersion, final String kind, final String namespace, final String name)
      throws HopException {
    if (!meta.isWaitReady()) {
      return;
    }
    // Generic "await an unknown kind" support: let the user provide the JSON pointer and the
    // expected value. Example: SparkApplication -> /status.applicationState.state == Completed.
    if (!Utils.isEmpty(meta.getAwaitField()) && !Utils.isEmpty(meta.getAwaitValue())) {
      final String pointerField = resolveVar(meta.getAwaitField());
      final String expectedValue = resolveVar(meta.getAwaitValue());
      final String path = data.service.resourcePath(apiVersion, kind, namespace, name);
      final HttpRequest getter =
          HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + path)).GET().build();
      logBasic(
          BaseMessages.getString(
              PKG, "KubernetesApply.Log.Awaiting", name, pointerField, expectedValue));
      final int timeout = meta.getWaitTimeoutSeconds() > 0 ? meta.getWaitTimeoutSeconds() : 120;
      final boolean ready = data.service.await(getter, pointerField, expectedValue, timeout, 2000);
      if (!ready) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "KubernetesApply.Error.NotReady", name, pointerField, expectedValue));
      }
      return;
    }
    final long deadline =
        System.currentTimeMillis()
            + (meta.getWaitTimeoutSeconds() > 0 ? meta.getWaitTimeoutSeconds() : 120) * 1000L;
    final KubernetesComponentAwaiter.Result result =
        data.service.awaitReady(apiVersion, kind, namespace, name, deadline, 2000);
    if (result == KubernetesComponentAwaiter.Result.FAILED) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesApply.Error.ComponentFailed", kind, name));
    }
    if (result == KubernetesComponentAwaiter.Result.TIMEOUT) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesApply.Error.NotReadyInTime", kind, name));
    }
    if (result == KubernetesComponentAwaiter.Result.NOT_HANDLED) {
      logBasic(BaseMessages.getString(PKG, "KubernetesApply.Log.NoReadinessForKind", kind));
    }
  }

  private String resolveVar(final String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
