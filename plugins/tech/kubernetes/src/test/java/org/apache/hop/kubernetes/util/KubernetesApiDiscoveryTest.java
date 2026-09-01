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

package org.apache.hop.kubernetes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesApiDiscoveryTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1",
        exchange -> {
          final String body =
              """
              {"kind":"APIResourceList","groupVersion":"v1","resources":[
                {"name":"pods","singularName":"","namespaced":true,"kind":"Pod","verbs":["get"]},
                {"name":"namespaces","singularName":"","namespaced":false,"kind":"Namespace","verbs":["get"]}
              ]}
              """;
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes().length);
          exchange.getResponseBody().write(body.getBytes());
          exchange.close();
        });
    server.createContext(
        "/apis",
        exchange -> {
          final String body =
              """
              {"kind":"APIGroupList","apiVersion":"v1","groups":[
                {"name":"apps","versions":[{"groupVersion":"apps/v1"}]},
                {"name":"batch","versions":[{"groupVersion":"batch/v1"}]}
              ]}
              """;
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes().length);
          exchange.getResponseBody().write(body.getBytes());
          exchange.close();
        });
    server.createContext(
        "/apis/apps/v1",
        exchange -> {
          final String body =
              """
              {"kind":"APIResourceList","apiVersion":"v1","groupVersion":"apps/v1","resources":[
                {"name":"deployments","singularName":"","namespaced":true,"kind":"Deployment","verbs":["get"]}
              ]}
              """;
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes().length);
          exchange.getResponseBody().write(body.getBytes());
          exchange.close();
        });
    server.createContext(
        "/apis/batch/v1",
        exchange -> {
          // resources include a subresource (jobs/status) which shares the parent kind "Job" and
          // must not overwrite the "jobs" plural
          final String body =
              """
              {"kind":"APIResourceList","apiVersion":"v1","groupVersion":"batch/v1","resources":[
                {"name":"jobs","singularName":"","namespaced":true,"kind":"Job","verbs":["get"]},
                {"name":"jobs/status","singularName":"","namespaced":true,"kind":"Job","verbs":["get","patch"]}
              ]}
              """;
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes().length);
          exchange.getResponseBody().write(body.getBytes());
          exchange.close();
        });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private KubernetesClient client() {
    return new KubernetesClient(
        new KubernetesClientConfiguration().setMaster("http://127.0.0.1:" + port));
  }

  @Test
  void pluralsAndNamespacingFromDiscovery() throws IOException {
    try (KubernetesClient client = client();
        JsonMapper mapper = KubernetesUtil.createMapper()) {
      final KubernetesApiDiscovery discovery = new KubernetesApiDiscovery(client, mapper);
      assertEquals("deployments", discovery.pluralOrGuess("apps/v1", "Deployment"));
      assertTrue(discovery.namespacedOrGuess("apps/v1", "Deployment"));
      assertEquals("pods", discovery.pluralOrGuess("v1", "Pod"));
      assertEquals("apps/v1", discovery.apiVersionOrGuess("Deployment"));
      assertEquals("jobs", discovery.pluralOrGuess("batch/v1", "Job"));
      assertEquals(
          "/apis/batch/v1/namespaces/default/jobs",
          discovery.resourcePath("batch/v1", "Job", null, null));
    }
  }

  @Test
  void clusterScopedFromDiscovery() throws IOException {
    try (KubernetesClient client = client();
        JsonMapper mapper = KubernetesUtil.createMapper()) {
      final KubernetesApiDiscovery discovery = new KubernetesApiDiscovery(client, mapper);
      assertFalse(discovery.namespacedOrGuess("v1", "Namespace"));
      assertEquals("/api/v1/namespaces", discovery.resourcePath("v1", "Namespace", null, null));
    }
  }

  @Test
  void fallbackWhenDiscoveryUnreachable() throws IOException {
    // point at a server that is not running (port 1) -> discovery returns null -> fallback rules
    try (KubernetesClient client =
            new KubernetesClient(
                new KubernetesClientConfiguration().setMaster("http://127.0.0.1:1"));
        JsonMapper mapper = KubernetesUtil.createMapper()) {
      final KubernetesApiDiscovery discovery = new KubernetesApiDiscovery(client, mapper);
      assertEquals("deployments", discovery.pluralOrGuess("apps/v1", "Deployment"));
      assertEquals("apps/v1", discovery.apiVersionOrGuess("Deployment"));
      assertTrue(discovery.namespacedOrGuess("v1", "Service"));
    }
  }
}
