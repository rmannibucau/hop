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

package org.apache.hop.pipeline.transforms.kubernetes.delete;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesApiService;

public class KubernetesDelete extends BaseTransform<KubernetesDeleteMeta, KubernetesDeleteData> {

  private static final Class<?> PKG = KubernetesApiService.class;

  private int outputFieldIndex = -1;
  private int nameFieldIndex = -1;

  public KubernetesDelete(
      final TransformMeta transformMeta,
      final KubernetesDeleteMeta meta,
      final KubernetesDeleteData data,
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
      if (getInputRowMeta() != null) {
        if (!Utils.isEmpty(meta.getNameFieldName())) {
          nameFieldIndex = getInputRowMeta().indexOfValue(meta.getNameFieldName());
        }
      }
    }
    if (r != null) {
      final String name = resolveName(r);
      if (Utils.isEmpty(name)) {
        KubernetesApiService.failRow(
            this, r, BaseMessages.getString(PKG, "KubernetesDelete.Error.NoName"));
        return true;
      }
      try {
        final Object[] outputRow = deleteResource(r, name);
        putRow(data.outputRowMeta, outputRow);
        incrementLinesOutput();
      } catch (final Exception e) {
        KubernetesApiService.failRow(this, r, e.getMessage());
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
              "KubernetesDelete");
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
    outputFieldIndex = data.outputRowMeta.size() - 3;
  }

  private String resolveName(final Object[] inputRow) throws HopException {
    if (nameFieldIndex >= 0 && inputRow != null) {
      final String fromField = data.outputRowMeta.getString(inputRow, nameFieldIndex);
      if (!Utils.isEmpty(fromField)) {
        return resolveVar(fromField);
      }
    }
    return resolveVar(meta.getName());
  }

  private Object[] deleteResource(final Object[] inputRow, final String name) throws HopException {
    final String namespace = resolveVar(meta.getNamespace());
    final String path =
        data.service.resourcePath(
            resolveVar(meta.getApiVersion()), resolveVar(meta.getKind()), namespace, name);
    final IRowMeta rowMeta = data.outputRowMeta;
    final Object[] outputRow = new Object[rowMeta.size()];
    for (int i = 0; i < inputRow.length; i++) {
      outputRow[i] = inputRow[i];
    }
    logBasic(
        BaseMessages.getString(
            PKG, "KubernetesDelete.Log.Deleting", resolveVar(meta.getKind()), name, namespace));
    try {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://kubernetes.api" + path))
              .DELETE()
              .build();
      final HttpResponse<String> response = data.service.client().send(request);
      final int status = response.statusCode();
      boolean deleted = status >= 200 && status < 300;
      if (status == 404 && meta.isIgnoreNotFound()) {
        deleted = false;
      } else if (status < 200 || status >= 300) {
        throw new HopException(
            "Kubernetes delete failed with HTTP " + status + ": " + response.body());
      }
      outputRow[outputFieldIndex] = deleted;
      outputRow[outputFieldIndex + 1] = namespace;
      outputRow[outputFieldIndex + 2] = name;
      logBasic(
          BaseMessages.getString(
              PKG,
              "KubernetesDelete.Log.Deleted",
              resolveVar(meta.getKind()),
              name,
              namespace,
              deleted));
      return outputRow;
    } catch (final HopException e) {
      throw e;
    } catch (final Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesDelete.Error.DeleteFailed", e.getMessage()), e);
    }
  }

  private String resolveVar(final String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
