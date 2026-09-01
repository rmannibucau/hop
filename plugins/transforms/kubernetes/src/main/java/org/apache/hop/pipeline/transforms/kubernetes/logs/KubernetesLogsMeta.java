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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
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
    id = "KubernetesLogs",
    image = "kubernetes-logs.svg",
    documentationUrl = "/pipeline/transforms/kubernetes/kubernetes-logs.html",
    name = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesLogs.name",
    description = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesLogs.description",
    keywords = "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesLogs.keyword",
    categoryDescription =
        "i18n:org.apache.hop.pipeline.transforms.kubernetes:KubernetesLogs.category")
public class KubernetesLogsMeta
    extends BaseKubernetesTransformMeta<KubernetesLogs, KubernetesLogsData> {

  @HopMetadataProperty(key = "pod")
  private String podName;

  @HopMetadataProperty(key = "pod_name_field")
  private String podNameField;

  @HopMetadataProperty(key = "container")
  private String container;

  @HopMetadataProperty(key = "tail_lines")
  private int tailLines;

  @HopMetadataProperty(key = "output_field_name")
  private String outputFieldName;

  public KubernetesLogsMeta() {
    super();
  }

  @Override
  public void setDefault() {
    connectionName = null;
    podName = null;
    podNameField = null;
    namespace = "";
    container = "";
    tailLines = 0;
    outputFieldName = "log_line";
  }

  @Override
  public void getFields(
      final IRowMeta inputRowMeta,
      final String name,
      final IRowMeta[] info,
      final TransformMeta nextTransform,
      final IVariables variables,
      final IHopMetadataProvider metadataProvider) {
    inputRowMeta.addValueMeta(new ValueMetaBoolean(outputFieldName));
    inputRowMeta.addValueMeta(new ValueMetaString("namespace"));
    inputRowMeta.addValueMeta(new ValueMetaString("name"));
    inputRowMeta.addValueMeta(new ValueMetaString("log_line"));
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
