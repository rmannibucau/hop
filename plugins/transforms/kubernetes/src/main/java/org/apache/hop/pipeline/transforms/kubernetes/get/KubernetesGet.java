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

package org.apache.hop.pipeline.transforms.kubernetes.get;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.model.KubernetesObject;
import org.apache.hop.kubernetes.model.KubernetesObjectMetadata;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesApiService;

public class KubernetesGet extends BaseTransform<KubernetesGetMeta, KubernetesGetData> {

  private static final Class<?> PKG = KubernetesApiService.class;

  private int nameFieldIndex = -1;

  public KubernetesGet(
      final TransformMeta transformMeta,
      final KubernetesGetMeta meta,
      final KubernetesGetData data,
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
      if (data.isReceivingInput && getInputRowMeta() != null) {
        nameFieldIndex = getInputRowMeta().indexOfValue("name");
      }
    }
    if (r != null || !data.isReceivingInput) {
      try {
        final Object[] outputRow = fetchResource(r);
        if (outputRow != null) {
          putRow(data.outputRowMeta, outputRow);
          incrementLinesOutput();
        }
      } catch (final Exception e) {
        KubernetesApiService.failRow(this, r, e.getMessage());
      }
      if (!data.isReceivingInput) {
        setOutputDone();
        return false;
      }
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
              "KubernetesGet");
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
    data.isReceivingInput = !getPipelineMeta().findPreviousTransforms(getTransformMeta()).isEmpty();
    if (data.isReceivingInput && getInputRowMeta() != null) {
      data.outputRowMeta = getInputRowMeta().clone();
    } else {
      data.outputRowMeta = new RowMeta();
    }
    meta.getFields(
        data.outputRowMeta, getTransformName(), null, null, variables, getMetadataProvider());
  }

  private Object[] fetchResource(final Object[] inputRow) throws HopException {
    String resourceName = resolveVar(meta.getName());
    if (data.isReceivingInput
        && Utils.isEmpty(resourceName)
        && inputRow != null
        && nameFieldIndex >= 0) {
      resourceName = data.outputRowMeta.getString(inputRow, nameFieldIndex);
    }
    if (Utils.isEmpty(resourceName) && !Utils.isEmpty(meta.getLabelSelector())) {
      try {
        resourceName =
            data.service.resolveResourceName(
                resolveVar(meta.getApiVersion()),
                resolveVar(meta.getKind()),
                resolveVar(meta.getNamespace()),
                resourceName,
                resolveVar(meta.getLabelSelector()));
      } catch (final Exception e) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "KubernetesGet.Error.LabelSelector", meta.getLabelSelector(), e.getMessage()),
            e);
      }
    }
    if (Utils.isEmpty(resourceName)) {
      resourceName = "";
    }
    final String namespace = resolveVar(meta.getNamespace());
    final String path =
        data.service.resourcePath(
            resolveVar(meta.getApiVersion()), resolveVar(meta.getKind()), namespace, resourceName);
    logBasic(
        BaseMessages.getString(
            PKG, "KubernetesGet.Log.Getting", resolveVar(meta.getKind()), resourceName, namespace));
    try {
      final HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + path)).GET().build();
      final HttpResponse<String> response = data.service.client().send(request);
      if (response.statusCode() == 404) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "KubernetesGet.Error.NotFound", resolveVar(meta.getKind()), resourceName));
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new HopException(
            "Kubernetes Get failed with HTTP " + response.statusCode() + ": " + response.body());
      }
      final Object[] outputRow = new Object[data.outputRowMeta.size()];
      if (meta.isOutputJson()) {
        outputRow[0] = response.body();
      } else {
        final KubernetesObject object =
            data.service.parseObject(KubernetesObject.class, response.body());
        final KubernetesObjectMetadata metadata = object == null ? null : object.metadata();
        outputRow[0] = response.body();
        outputRow[1] = metadata == null ? null : metadata.name();
        outputRow[2] = metadata == null ? null : metadata.namespace();
        outputRow[3] = metadata == null ? null : metadata.uid();
        outputRow[4] = metadata == null ? null : metadata.creationTimestamp();
      }
      return outputRow;
    } catch (final HopException e) {
      throw e;
    } catch (final Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesGet.Error.GetFailed", e.getMessage()), e);
    }
  }

  private String resolveVar(final String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
