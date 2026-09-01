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

package org.apache.hop.pipeline.transforms.kubernetes.list;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.kubernetes.model.KubernetesObject;
import org.apache.hop.kubernetes.model.KubernetesObjectList;
import org.apache.hop.kubernetes.model.KubernetesObjectMetadata;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.KubernetesApiService;

public class KubernetesList extends BaseTransform<KubernetesListMeta, KubernetesListData> {

  private static final Class<?> PKG = KubernetesApiService.class;

  public KubernetesList(
      final TransformMeta transformMeta,
      final KubernetesListMeta meta,
      final KubernetesListData data,
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
    if (data.iterator == null) {
      try {
        listResources();
      } catch (final HopException e) {
        KubernetesApiService.failRow(this, r, e.getMessage());
        data.iterator = java.util.Collections.emptyIterator();
      }
    }
    if (data.iterator != null && data.iterator.hasNext()) {
      final Object[] row = data.iterator.next();
      putRow(data.outputRowMeta, row);
      incrementLinesOutput();
      return true;
    }
    if (r != null) {
      putRow(data.outputRowMeta, r);
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
              "KubernetesList");
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

  private void listResources() throws HopException {
    final String namespace = resolveVar(meta.getNamespace());
    final String kind = resolveVar(meta.getKind());
    final String apiVersion = resolveVar(meta.getApiVersion());
    final String plural = data.service.pluralOf(apiVersion, kind);
    final StringBuilder url =
        new StringBuilder(data.service.resourcePath(apiVersion, kind, namespace, null));
    int params = 0;
    if (!Utils.isEmpty(meta.getLabelSelector())) {
      params++;
      url.append("?labelSelector=")
          .append(data.service.urlEncode(resolveVar(meta.getLabelSelector())));
    }
    if (meta.getLimit() > 0) {
      url.append(params == 0 ? '?' : '&').append("limit=").append(meta.getLimit());
    }
    logBasic(BaseMessages.getString(PKG, "KubernetesList.Log.Listing", plural, namespace));
    try {
      final HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("https://kubernetes.api" + url)).GET().build();
      final HttpResponse<String> response = data.service.client().send(request);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new HopException(
            "Kubernetes List failed with HTTP " + response.statusCode() + ": " + response.body());
      }
      final KubernetesObjectList list =
          data.service.parseObject(KubernetesObjectList.class, response.body());
      final List<Object[]> rows = new ArrayList<>();
      if (list != null && list.items() != null) {
        for (final KubernetesObject item : list.items()) {
          final Object[] row = toRow(item);
          if (row != null) {
            rows.add(row);
          }
        }
      }
      data.rows = rows;
      data.iterator = rows.iterator();
      logBasic(BaseMessages.getString(PKG, "KubernetesList.Log.Listed", plural, rows.size()));
    } catch (final Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "KubernetesList.Error.ListFailed", e.getMessage()), e);
    }
  }

  private Object[] toRow(final KubernetesObject object) {
    if (object == null) {
      return null;
    }
    final KubernetesObjectMetadata metadata = object.metadata();
    if (metadata == null) {
      return null;
    }
    final IRowMeta rowMeta = data.outputRowMeta;
    final Object[] row = new Object[rowMeta.size()];
    row[0] = metadata.name();
    row[1] = metadata.namespace();
    row[2] = resolveVar(meta.getKind());
    row[3] = object.apiVersion();
    row[4] = metadata.uid();
    row[5] = metadata.creationTimestamp();
    if (meta.isIncludeStatus()) {
      row[6] = object.status() == null ? null : data.service.render(object.status());
    }
    return row;
  }

  private String resolveVar(final String value) {
    return variables != null ? variables.resolve(value) : value;
  }
}
