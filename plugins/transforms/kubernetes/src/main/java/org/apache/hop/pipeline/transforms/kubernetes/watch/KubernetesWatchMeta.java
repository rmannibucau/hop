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
    id = "KubernetesWatch",
    image = "kubernetes-watch.svg",
    documentationUrl = "/pipeline/transforms/kubernetes/kubernetes-watch.html",
    name = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesWatch.name",
    description = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesWatch.description",
    keywords = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesWatch.keyword",
    categoryDescription =
        "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesWatch.category")
public class KubernetesWatchMeta
    extends BaseKubernetesTransformMeta<KubernetesWatch, KubernetesWatchData> {

  @HopMetadataProperty(key = "kind")
  private String kind;

  @HopMetadataProperty(key = "output_field_name")
  private String outputFieldName;

  @HopMetadataProperty(key = "include_body")
  private boolean includeBody;

  public KubernetesWatchMeta() {
    super();
  }

  @Override
  public void setDefault() {
    connectionName = null;
    kind = "pods";
    namespace = "";
    labelSelector = null;
    outputFieldName = "watch_event";
    includeBody = false;
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
    inputRowMeta.addValueMeta(new ValueMetaString("name"));
    inputRowMeta.addValueMeta(new ValueMetaString("namespace"));
    inputRowMeta.addValueMeta(new ValueMetaString("kind"));
    if (includeBody) {
      inputRowMeta.addValueMeta(new ValueMetaString("body"));
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
