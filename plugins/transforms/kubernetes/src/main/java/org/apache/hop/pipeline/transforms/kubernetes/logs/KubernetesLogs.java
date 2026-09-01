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

package org.apache.hop.pipeline.transforms.kubernetes.logs;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesApiService;

public class KubernetesLogs extends BaseTransform<KubernetesLogsMeta, KubernetesLogsData> {

  private static final Class<?> PKG = KubernetesApiService.class;

  private String podName;
  private int podNameFieldIndex = -1;
  private boolean logsLoaded = false;

  public KubernetesLogs(
      final TransformMeta transformMeta,
      final KubernetesLogsMeta meta,
      final KubernetesLogsData data,
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
        if (!Utils.isEmpty(meta.getPodNameField())) {
          podNameFieldIndex = getInputRowMeta().indexOfValue(meta.getPodNameField());
        }
      }
    }
    if (r != null && !logsLoaded) {
      podName = resolvePodName(r);
      try {
        loadLogs();
      } catch (final Exception e) {
        KubernetesApiService.failRow(this, r, e.getMessage());
      }
      logsLoaded = true;
    }
    if (!logsLoaded && !data.isReceivingInput) {
      podName = resolvePodName(null);
      try {
        loadLogs();
      } catch (final Exception e) {
        KubernetesApiService.failRow(this, null, e.getMessage());
      }
      logsLoaded = true;
    }
    if (data.iterator != null && data.iterator.hasNext()) {
      putRow(data.outputRowMeta, data.iterator.next());
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
              "KubernetesLogs");
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

  private String resolvePodName(final Object[] inputRow) throws HopException {
    if (podNameFieldIndex >= 0 && inputRow != null) {
      final String fromField = data.outputRowMeta.getString(inputRow, podNameFieldIndex);
      if (!Utils.isEmpty(fromField)) {
        return resolveVar(fromField);
      }
    }
    return resolveVar(meta.getPodName());
  }

  private void loadLogs() throws HopException {
    if (Utils.isEmpty(podName)) {
      throw new HopException(BaseMessages.getString(PKG, "KubernetesLogs.Error.NoPod"));
    }
    final String namespace = resolveVar(meta.getNamespace());
    final StringBuilder path =
        new StringBuilder("/api/v1/namespaces/")
            .append(data.service.urlEncode(namespace))
            .append("/pods/")
            .append(data.service.urlEncode(podName))
            .append("/log");
    int params = 0;
    if (!Utils.isEmpty(meta.getContainer())) {
      path.append(params++ == 0 ? '?' : '&')
          .append("container=")
          .append(data.service.urlEncode(resolveVar(meta.getContainer())));
    }
    if (meta.getTailLines() > 0) {
      path.append(params++ == 0 ? '?' : '&').append("tailLines=").append(meta.getTailLines());
    }
    logBasic(BaseMessages.getString(PKG, "KubernetesLogs.Log.Reading", podName, namespace));
    try {
      final HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + path)).GET().build();
      final HttpResponse<String> response = data.service.client().send(request);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new HopException(
            "Kubernetes logs failed with HTTP " + response.statusCode() + ": " + response.body());
      }
      final List<Object[]> rows = new ArrayList<>();
      final String body = response.body();
      if (body != null && !body.isEmpty()) {
        final String[] lines = body.split("\\r?\\n");
        for (final String line : lines) {
          final Object[] row = new Object[data.outputRowMeta.size()];
          final int base = data.outputRowMeta.size() - 4;
          row[base] = Boolean.TRUE;
          row[base + 1] = namespace;
          row[base + 2] = podName;
          row[base + 3] = line;
          rows.add(row);
        }
      }
      data.rows = rows;
      data.iterator = rows.iterator();
      logBasic(BaseMessages.getString(PKG, "KubernetesLogs.Log.Read", rows.size(), podName));
    } catch (final Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesLogs.Error.LogsFailed", e.getMessage()), e);
    }
  }

  private String resolveVar(final String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
