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

package org.apache.hop.pipeline.transforms.kubernetes;

/**
 * Structured result of a Kubernetes operation (apply, delete, run, scale, ...). Carries the success
 * flag plus the addressed resource so downstream transforms can reuse it.
 */
public record KubernetesOperationResult(boolean ok, String namespace, String name) {

  public static KubernetesOperationResult of(
      final boolean ok, final String namespace, final String name) {
    return new KubernetesOperationResult(ok, namespace, name);
  }
}
