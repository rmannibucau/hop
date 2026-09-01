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
import java.util.List;

/**
 * Typed view of the {@code status} sub-object shared by workload kinds (Deployment, ReplicaSet,
 * ReplicationController, Job, ...). Integer fields are boxed so an absent member maps to {@code
 * null} instead of {@code 0}.
 */
@JsonModel
public record KubernetesStatus(
    Integer replicas,
    Integer readyReplicas,
    Integer availableReplicas,
    Integer observedGeneration,
    Integer failed,
    Integer succeeded,
    Integer active,
    List<KubernetesCondition> conditions) {
  public int observedOrDefault() {
    return observedGeneration == null ? -1 : observedGeneration;
  }

  public int failedOrDefault() {
    return failed == null ? 0 : failed;
  }

  public int succeededOrDefault() {
    return succeeded == null ? 0 : succeeded;
  }
}
