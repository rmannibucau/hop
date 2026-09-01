/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.kubernetes.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

/**
 * Typed {@code batch/v1} Job declaration used to create standalone jobs. Absent members map to
 * {@code null} and are omitted during serialization.
 */
@JsonModel
public record KubernetesJob(
    String apiVersion, String kind, KubernetesObjectMetadata metadata, KubernetesJobSpec spec) {
  /** Factory for a minimal standalone {@code batch/v1} Job. */
  public static KubernetesJob of(
      final String name, final String namespace, final KubernetesJobSpec spec) {
    return new KubernetesJob(
        "batch/v1",
        "Job",
        new KubernetesObjectMetadata(name, namespace, null, null, null, null, null),
        spec);
  }
}
