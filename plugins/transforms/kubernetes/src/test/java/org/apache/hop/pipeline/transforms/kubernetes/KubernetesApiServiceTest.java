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

package org.apache.hop.pipeline.transforms.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesApiServiceTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void init() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private KubernetesApiService service() {
    final KubernetesConnection connection = new KubernetesConnection("conn");
    connection.setMaster("http://127.0.0.1:" + port);
    connection.setNamespace("ns1");
    return new KubernetesApiService(connection, new Variables());
  }

  @Test
  void pluralOfHandlesKinds() {
    server.start();
    try (final KubernetesApiService service = service()) {
      assertEquals("pods", service.pluralOf("v1", "Pod"));
      assertEquals("deployments", service.pluralOf("apps/v1", "Deployment"));
      assertEquals("configmaps", service.pluralOf("v1", "ConfigMap"));
      assertEquals("services", service.pluralOf("v1", "Service"));
    }
  }

  @Test
  void resourcePathResolvesGroupAndNamespace() {
    server.start();
    try (final KubernetesApiService service = service()) {
      assertEquals(
          "/apis/apps/v1/namespaces/default/deployments",
          service.resourcePath("apps/v1", "Deployment", null, null));
      assertEquals(
          "/api/v1/namespaces/ns1/configmaps/my-cm",
          service.resourcePath("v1", "ConfigMap", "ns1", "my-cm"));
    }
  }

  @Test
  void parseObjectAndRenderRoundTrip() {
    try (final KubernetesApiService service = service()) {
      final Map<String, Object> object =
          service.parseObject(
              "{\"apiVersion\":\"v1\",\"kind\":\"Pod\",\"metadata\":{\"name\":\"p1\"}}");
      assertEquals("Pod", object.get("kind"));
      final String rendered = service.render(object);
      assertTrue(rendered.contains("\"p1\""), "rendered JSON should keep metadata name");
    }
  }

  @Test
  void metadataOfExtractsNameAndNamespace() {
    try (final KubernetesApiService service = service()) {
      final Map<String, Object> object =
          service.parseObject(
              "{\"metadata\":{\"name\":\"web\",\"namespace\":\"prod\",\"labels\":{\"a\":\"b\"}}}");
      assertEquals("web", service.metadataOf(object).name());
      assertEquals("prod", service.metadataOf(object).namespace());
    }
  }

  @Test
  void resolveNamespaceFallsBackToConnection() {
    try (final KubernetesApiService service = service()) {
      assertEquals("explicit", service.resolveNamespace("explicit"));
      assertEquals("ns1", service.resolveNamespace(null));
    }
  }

  @Test
  void awaitMatchesPointerOnLiveServer() {
    server.createContext(
        "/api/v1/namespaces/ns1/pods/stable",
        exchange -> {
          final String body = "{\"status\":{\"phase\":\"Running\"}}";
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
          exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();
    try (final KubernetesApiService service = service()) {
      final HttpRequest getter =
          HttpRequest.newBuilder()
              .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/namespaces/ns1/pods/stable"))
              .GET()
              .build();
      assertTrue(service.await(getter, "/status/phase", "Running", 5, 20));
    }
  }
}
