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

package org.apache.hop.kubernetes.metadata;

import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataCategory;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Kubernetes connection metadata. Maps almost 1:1 onto the fusion {@code
 * KubernetesClientConfiguration}; most fields are overrides on top of a kubeconfig file or the
 * in-cluster service account.
 */
@GuiPlugin
@SuppressWarnings("java:S2160")
@HopMetadata(
    key = "kubernetes-connection",
    name = "i18n::KubernetesConnection.name",
    description = "i18n::KubernetesConnection.description",
    image = "kubernetes.svg",
    category = HopMetadataCategory.CONNECTIONS,
    documentationUrl = "/metadata-types/kubernetes/kubernetes-connection.html",
    hopMetadataPropertyType = HopMetadataPropertyType.KUBERNETES_CONNECTION,
    supportsGlobalReplace = true)
public class KubernetesConnection extends HopMetadataBase implements IHopMetadata {

  public static final String WIDGET_ID_MASTER = "20000-master";
  public static final String WIDGET_ID_KUBECONFIG_FILE = "20100-kubeconfig-file";
  public static final String WIDGET_ID_CONTEXT = "20200-context";
  public static final String WIDGET_ID_NAMESPACE = "20300-namespace";
  public static final String WIDGET_ID_TOKEN = "20400-token";
  public static final String WIDGET_ID_CA_CERT_FILE = "20500-ca-cert-file";
  public static final String WIDGET_ID_CLIENT_CERT_FILE = "20600-client-cert-file";
  public static final String WIDGET_ID_CLIENT_KEY_FILE = "20700-client-key-file";
  public static final String WIDGET_ID_SKIP_TLS = "20800-skip-tls";
  public static final String WIDGET_ID_CONNECT_TIMEOUT = "20900-connect-timeout";
  public static final String WIDGET_ID_REQUEST_TIMEOUT = "21000-request-timeout";

  /** Master API URL. Empty = derived from kubeconfig or in-cluster service account. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_MASTER,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.Master.Label",
      toolTip = "i18n::KubernetesMetadata.Master.ToolTip")
  private String master;

  /** Path to the kubeconfig file, supports variables. Default ${KUBECONFIG} or ~/.kube/config. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_KUBECONFIG_FILE,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.KubeconfigFile.Label",
      toolTip = "i18n::KubernetesMetadata.KubeconfigFile.ToolTip")
  private String kubeconfigFile;

  /** Context name inside the kubeconfig. Empty = current context. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_CONTEXT,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.Context.Label",
      toolTip = "i18n::KubernetesMetadata.Context.ToolTip")
  private String context;

  /** Default namespace for all operations, supports variables. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_NAMESPACE,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.Namespace.Label",
      toolTip = "i18n::KubernetesMetadata.Namespace.ToolTip")
  private String namespace;

  /** Path to a token file, supports variables/secret resolvers. Empty = from kubeconfig. */
  @HopMetadataProperty(password = true)
  @GuiWidgetElement(
      id = WIDGET_ID_TOKEN,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.Token.Label",
      toolTip = "i18n::KubernetesMetadata.Token.ToolTip",
      password = true)
  private String token;

  /** Path to PEM CA certificate bundle. Empty = from kubeconfig. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_CA_CERT_FILE,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.CaCertFile.Label",
      toolTip = "i18n::KubernetesMetadata.CaCertFile.ToolTip")
  private String caCertFile;

  /** Client certificate for mutual TLS. Empty = from kubeconfig. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_CLIENT_CERT_FILE,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.ClientCertFile.Label",
      toolTip = "i18n::KubernetesMetadata.ClientCertFile.ToolTip")
  private String clientCertFile;

  /** Client private key for mutual TLS, used with the client certificate. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_CLIENT_KEY_FILE,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.ClientKeyFile.Label",
      toolTip = "i18n::KubernetesMetadata.ClientKeyFile.ToolTip")
  private String clientKeyFile;

  /** Skip TLS verification (dev only). */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_SKIP_TLS,
      type = GuiElementType.CHECKBOX,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.SkipTls.Label",
      toolTip = "i18n::KubernetesMetadata.SkipTls.ToolTip")
  private boolean skipTls;

  /** Connection timeout in seconds on the JDK HttpClient. Default 10. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_CONNECT_TIMEOUT,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.ConnectTimeout.Label",
      toolTip = "i18n::KubernetesMetadata.ConnectTimeout.ToolTip")
  private int connectTimeout = 10;

  /** Request timeout in seconds on the JDK HttpClient. Default 120. */
  @HopMetadataProperty
  @GuiWidgetElement(
      id = WIDGET_ID_REQUEST_TIMEOUT,
      type = GuiElementType.TEXT,
      parentId = KubernetesConnectionEditor.CONNECTION_WIDGET_ID,
      label = "i18n::KubernetesMetadata.RequestTimeout.Label",
      toolTip = "i18n::KubernetesMetadata.RequestTimeout.ToolTip")
  private int requestTimeout = 120;

  public KubernetesConnection() {
    this("");
  }

  public KubernetesConnection(final String name) {
    super(name);
  }

  public KubernetesConnection(final KubernetesConnection c) {
    super(c);
    this.master = c.master;
    this.kubeconfigFile = c.kubeconfigFile;
    this.context = c.context;
    this.namespace = c.namespace;
    this.token = c.token;
    this.caCertFile = c.caCertFile;
    this.clientCertFile = c.clientCertFile;
    this.clientKeyFile = c.clientKeyFile;
    this.skipTls = c.skipTls;
    this.connectTimeout = c.connectTimeout;
    this.requestTimeout = c.requestTimeout;
  }

  public String getMaster() {
    return master;
  }

  public void setMaster(final String master) {
    this.master = master;
  }

  public String getKubeconfigFile() {
    return kubeconfigFile;
  }

  public void setKubeconfigFile(final String kubeconfigFile) {
    this.kubeconfigFile = kubeconfigFile;
  }

  public String getContext() {
    return context;
  }

  public void setContext(final String context) {
    this.context = context;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(final String namespace) {
    this.namespace = namespace;
  }

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  public String getCaCertFile() {
    return caCertFile;
  }

  public void setCaCertFile(final String caCertFile) {
    this.caCertFile = caCertFile;
  }

  public String getClientCertFile() {
    return clientCertFile;
  }

  public void setClientCertFile(final String clientCertFile) {
    this.clientCertFile = clientCertFile;
  }

  public String getClientKeyFile() {
    return clientKeyFile;
  }

  public void setClientKeyFile(final String clientKeyFile) {
    this.clientKeyFile = clientKeyFile;
  }

  public boolean isSkipTls() {
    return skipTls;
  }

  public void setSkipTls(final boolean skipTls) {
    this.skipTls = skipTls;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(final int connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public int getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final int requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
}
