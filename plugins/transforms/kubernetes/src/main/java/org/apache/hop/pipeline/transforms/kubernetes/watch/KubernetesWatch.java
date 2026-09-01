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

package org.apache.hop.pipeline.transforms.kubernetes.watch;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.model.KubernetesObjectMetadata;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesApiService;

public class KubernetesWatch extends BaseTransform<KubernetesWatchMeta, KubernetesWatchData> {

  private static final Class<?> PKG = KubernetesApiService.class;

  private boolean watchLoaded = false;

  public KubernetesWatch(
      final TransformMeta transformMeta,
      final KubernetesWatchMeta meta,
      final KubernetesWatchData data,
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
    }
    if (!watchLoaded) {
      try {
        loadWatch();
      } catch (final HopException e) {
        KubernetesApiService.failRow(this, r, e.getMessage());
      }
      watchLoaded = true;
    }
    if (data.iterator != null && data.iterator.hasNext()) {
      putRow(data.outputRowMeta, data.iterator.next());
      incrementLinesOutput();
      return true;
    }
    if (r != null) {
      putRow(data.outputRowMeta, r);
    }
    logBasic(BaseMessages.getString(PKG, "KubernetesWatch.Log.WatchEnd"));
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
              "KubernetesWatch");
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

  private void loadWatch() throws HopException {
    final String namespace = resolveVar(meta.getNamespace());
    final String kind = resolveVar(meta.getKind());
    final String apiVersion = resolveVar(meta.getApiVersion());
    final String plural = data.service.pluralOf(apiVersion, kind);
    final StringBuilder path =
        new StringBuilder(data.service.resourcePath(apiVersion, kind, namespace, null));
    path.append("?watch=true");
    if (!Utils.isEmpty(meta.getLabelSelector())) {
      path.append("&labelSelector=")
          .append(data.service.urlEncode(resolveVar(meta.getLabelSelector())));
    }
    logBasic(BaseMessages.getString(PKG, "KubernetesWatch.Log.Watching", plural, namespace));
    try {
      final HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + path)).GET().build();
      final HttpResponse<String> response = data.service.client().send(request);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new HopException(
            "Kubernetes watch failed with HTTP " + response.statusCode() + ": " + response.body());
      }
      final List<Object[]> rows = new ArrayList<>();
      final String body = response.body();
      if (body != null && !body.isEmpty()) {
        final String[] lines = body.split("\\r?\\n");
        for (final String line : lines) {
          if (line.trim().isEmpty()) {
            continue;
          }
          final Object[] row = toRow(line);
          if (row != null) {
            rows.add(row);
          }
        }
      }
      data.rows = rows;
      data.iterator = rows.iterator();
      logBasic(BaseMessages.getString(PKG, "KubernetesWatch.Log.Watched", rows.size()));
    } catch (final Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesWatch.Error.WatchFailed", e.getMessage()), e);
    }
  }

  private Object[] toRow(final String json) {
    final Map<String, Object> event = data.service.parseObject(json);
    if (event == null) {
      return null;
    }
    final Object rowObj = event.get("object");
    final KubernetesObjectMetadata metadata =
        rowObj == null ? null : objectMetadata((Map<?, ?>) rowObj);
    final Object[] row = new Object[data.outputRowMeta.size()];
    row[0] = event.get("type") == null ? null : String.valueOf(event.get("type"));
    row[1] = metadata == null ? null : metadata.name();
    row[2] = metadata == null ? null : metadata.namespace();
    row[3] = resolveVar(meta.getKind());
    if (meta.isIncludeBody()) {
      row[4] = rowObj == null ? null : data.service.render(rowObj);
    }
    return row;
  }

  private KubernetesObjectMetadata objectMetadata(final Map<?, ?> object) {
    final Object meta = object.get("metadata");
    return meta != null
        ? data.service.parseObject(KubernetesObjectMetadata.class, data.service.render(meta))
        : null;
  }

  private String resolveVar(final String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
