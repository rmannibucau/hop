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

package org.apache.hop.workflow.actions.kubernetes;

import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.workflow.action.ActionBase;

/**
 * Base class for all Kubernetes workflow actions. Holds the configuration every action shares
 * (target connection, namespace and the label selector used to address resources).
 */
public abstract class ActionKubernetesBase extends ActionBase {

  @HopMetadataProperty(
      key = "connection",
      hopMetadataPropertyType = HopMetadataPropertyType.KUBERNETES_CONNECTION)
  protected String connectionName;

  @HopMetadataProperty(key = "namespace")
  protected String namespace;

  @HopMetadataProperty(key = "label_selector")
  protected String labelSelector;

  public String getConnectionName() {
    return connectionName;
  }

  public void setConnectionName(final String connectionName) {
    this.connectionName = connectionName;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(final String namespace) {
    this.namespace = namespace;
  }

  public String getLabelSelector() {
    return labelSelector;
  }

  public void setLabelSelector(final String labelSelector) {
    this.labelSelector = labelSelector;
  }
}
