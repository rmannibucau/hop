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

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.ITransformData;

/**
 * Base metadata for all Kubernetes transforms. Holds the fields every transform shares (target
 * connection, namespace and the label selector used to address resources), so they are not
 * duplicated in each transform.
 */
@Getter
@Setter
public abstract class BaseKubernetesTransformMeta<
        Main extends ITransform, Data extends ITransformData>
    extends BaseTransformMeta<Main, Data> {

  @HopMetadataProperty(
      key = "connection",
      hopMetadataPropertyType = HopMetadataPropertyType.KUBERNETES_CONNECTION)
  protected String connectionName;

  @HopMetadataProperty(key = "namespace")
  protected String namespace;

  @HopMetadataProperty(key = "label_selector")
  protected String labelSelector;

  /**
   * Optional explicit API group/version override used for resource path resolution (e.g. {@code
   * apps/v1}, {@code v1}). When blank, the API group/version is discovered from the cluster and
   * falls back to well-known guesses for common kinds.
   */
  @HopMetadataProperty(key = "api_version")
  protected String apiVersion;
}
