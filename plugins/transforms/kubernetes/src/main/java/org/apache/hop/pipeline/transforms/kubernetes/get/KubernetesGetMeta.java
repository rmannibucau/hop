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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.kubernetes.BaseKubernetesTransformMeta;

@Getter
@Setter
@Transform(
    id = "KubernetesGet",
    image = "kubernetes-get.svg",
    documentationUrl = "/pipeline/transforms/kubernetes/kubernetes-get.html",
    name = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesGet.name",
    description = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesGet.description",
    keywords = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesGet.keyword",
    categoryDescription =
        "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesGet.category")
public class KubernetesGetMeta
    extends BaseKubernetesTransformMeta<KubernetesGet, KubernetesGetData> {

  @HopMetadataProperty(key = "kind")
  private String kind;

  @HopMetadataProperty(key = "name")
  private String name;

  @HopMetadataProperty(key = "output_field_name")
  private String outputFieldName;

  @HopMetadataProperty(key = "output_json")
  private boolean outputJson;

  public KubernetesGetMeta() {
    super();
  }

  @Override
  public void setDefault() {
    connectionName = null;
    kind = "deployment";
    name = null;
    namespace = "";
    outputFieldName = "resource_manifest";
    outputJson = true;
  }

  @Override
  public void getFields(
      final IRowMeta inputRowMeta,
      final String name,
      final IRowMeta[] info,
      final TransformMeta nextTransform,
      final IVariables variables,
      final IHopMetadataProvider metadataProvider) {
    inputRowMeta.addValueMeta(new ValueMetaString(outputFieldName));
    if (!outputJson) {
      inputRowMeta.addValueMeta(new ValueMetaString("name"));
      inputRowMeta.addValueMeta(new ValueMetaString("namespace"));
      inputRowMeta.addValueMeta(new ValueMetaString("resource_uid"));
      inputRowMeta.addValueMeta(new ValueMetaString("creationTimestamp"));
    }
  }

  @Override
  public void check(
      final List<ICheckResult> remarks,
      final PipelineMeta pipelineMeta,
      final TransformMeta transformMeta,
      final IRowMeta prev,
      final String[] input,
      final String[] output,
      final IRowMeta info,
      final IVariables variables,
      final IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(connectionName)) {
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
