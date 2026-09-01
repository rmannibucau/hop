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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.BaseKubernetesTransformMeta;

@Transform(
    id = "KubernetesList",
    image = "kubernetes-list.svg",
    documentationUrl = "/pipeline/transforms/kubernetes/kubernetes-list.html",
    name = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesList.name",
    description = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesList.description",
    keywords = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesList.keyword",
    categoryDescription =
        "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesList.category")
@Getter
@Setter
public class KubernetesListMeta
    extends BaseKubernetesTransformMeta<KubernetesList, KubernetesListData> {

  @HopMetadataProperty(key = "kind")
  private String kind;

  @HopMetadataProperty(key = "limit")
  private int limit;

  @HopMetadataProperty(key = "include_status")
  private boolean includeStatus;

  public KubernetesListMeta() {
    super();
  }

  @Override
  public void setDefault() {
    connectionName = null;
    kind = "pods";
    namespace = "";
    labelSelector = null;
    limit = 0;
    includeStatus = true;
  }

  @Override
  public void getFields(
      IRowMeta inputRowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    inputRowMeta.addValueMeta(new ValueMetaString("name"));
    inputRowMeta.addValueMeta(new ValueMetaString("namespace"));
    inputRowMeta.addValueMeta(new ValueMetaString("kind"));
    inputRowMeta.addValueMeta(new ValueMetaString("apiVersion"));
    inputRowMeta.addValueMeta(new ValueMetaString("resource_uid"));
    inputRowMeta.addValueMeta(new ValueMetaString("creationTimestamp"));
    if (includeStatus) {
      inputRowMeta.addValueMeta(new ValueMetaString("status"));
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      final TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (connectionName == null || connectionName.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              "No Kubernetes connection is configured",
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK, "Kubernetes connection is configured", transformMeta));
    }
  }

  @Override
  public boolean supportsErrorHandling() {
    return true;
  }
}
