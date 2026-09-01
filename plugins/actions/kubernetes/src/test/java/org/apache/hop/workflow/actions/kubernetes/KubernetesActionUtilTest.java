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

package org.apache.hop.workflow.actions.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesActionUtilTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void getPluralUsesLocaleRoot() {
    assertEquals("pods", KubernetesActionUtil.getPlural("Pod"));
    assertEquals("deployments", KubernetesActionUtil.getPlural("Deployment"));
    assertEquals("namespaces", KubernetesActionUtil.getPlural("Namespace"));
    assertEquals("policies", KubernetesActionUtil.getPlural("Policy"));
    assertEquals("services", KubernetesActionUtil.getPlural("Service"));
  }

  @Test
  void resourcePathDedupsAppend() {
    // namespaced resource -> namespace segment + plural once
    assertEquals(
        "/api/v1/namespaces/default/pods",
        KubernetesActionUtil.resourcePath("v1", "Pod", null, null));
    assertEquals(
        "/api/v1/namespaces/my-ns/secrets/my-secret",
        KubernetesActionUtil.resourcePath("v1", "Secret", "my-ns", "my-secret"));
    // group/version path (Deployment is namespaced -> namespace segment)
    assertEquals(
        "/apis/apps/v1/namespaces/default/deployments",
        KubernetesActionUtil.resourcePath("apps/v1", "Deployment", null, null));
    assertEquals(
        "/apis/apps/v1/namespaces/prod/deployments/web",
        KubernetesActionUtil.resourcePath("apps/v1", "Deployment", "prod", "web"));
    // cluster-scoped resource has no namespace segment
    assertEquals(
        "/api/v1/namespaces", KubernetesActionUtil.resourcePath("v1", "Namespace", null, null));
  }

  @Test
  void awaitWithPredicateAndJsonAccept() throws Exception {
    final AtomicInteger calls = new AtomicInteger();
    server.createContext(
        "/api/v1/pods/my-pod",
        exchange -> {
          calls.incrementAndGet();
          final int ready = calls.get() >= 2 ? 3 : 1;
          final String body = "{\"status\":{\"replicas\":3,\"readyReplicas\":" + ready + "}}";
          exchange.sendResponseHeaders(200, body.getBytes().length);
          exchange.getResponseBody().write(body.getBytes());
          exchange.close();
        });
    try (KubernetesClient client =
        new KubernetesClient(
            new KubernetesClientConfiguration().setMaster("http://127.0.0.1:" + port))) {
      try (io.yupiik.fusion.json.JsonMapper mapper = KubernetesActionUtil.createMapper()) {
        assertTrue(mapper != null);
      }
      final HttpRequest getter =
          HttpRequest.newBuilder()
              .uri(URI.create("https://kubernetes.api/api/v1/pods/my-pod"))
              .GET()
              .header("Accept", "application/json")
              .build();
      final boolean ready =
          KubernetesActionUtil.await(
              client,
              getter,
              "/status/readyReplicas",
              null,
              body -> {
                final Object status = body instanceof Map<?, ?> m ? m.get("status") : null;
                final Object r = status instanceof Map<?, ?> s ? s.get("readyReplicas") : null;
                return r instanceof Number n && n.intValue() >= 3;
              },
              System.currentTimeMillis() + 5000,
              50);
      assertTrue(ready, "await should succeed once replicated readiness is met");
      assertTrue(calls.get() >= 2, "expected more than one poll");
    }
  }

  @Test
  void awaitEquality() throws Exception {
    server.createContext(
        "/api/v1/pods/stable",
        exchange -> {
          final String body = "{\"status\":{\"phase\":\"Running\"}}";
          exchange.sendResponseHeaders(200, body.getBytes().length);
          exchange.getResponseBody().write(body.getBytes());
          exchange.close();
        });
    try (KubernetesClient client =
        new KubernetesClient(
            new KubernetesClientConfiguration().setMaster("http://127.0.0.1:" + port))) {
      final HttpRequest getter =
          HttpRequest.newBuilder()
              .uri(URI.create("https://kubernetes.api/api/v1/pods/stable"))
              .GET()
              .build();
      assertTrue(
          KubernetesActionUtil.await(
              client, getter, "/status/phase", "Running", System.currentTimeMillis() + 5000, 20));
      assertFalse(
          KubernetesActionUtil.await(
              client, getter, "/status/phase", "Pending", System.currentTimeMillis() + 5, 20));
    }
  }
}
