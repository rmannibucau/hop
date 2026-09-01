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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.Result;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionKubernetesApplyTest {

  private HttpServer server;
  private int port;
  private MemoryMetadataProvider provider;

  @BeforeEach
  void init() throws Exception {
    HopLogStore.init();
    HopClientEnvironment.init();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    port = server.getAddress().getPort();
    provider = new MemoryMetadataProvider();
    final KubernetesConnection connection = new KubernetesConnection("conn");
    connection.setMaster("http://127.0.0.1:" + port);
    connection.setNamespace("ns1");
    provider.getSerializer(KubernetesConnection.class).save(connection);
  }

  @AfterEach
  void stop() {
    server.stop(0);
    provider = null;
  }

  private static final String MANIFEST =
      """
      {"apiVersion":"v1","kind":"ConfigMap","metadata":{"name":"my-cm","namespace":"ns1"},"data":{"a":"b"}}
      """;

  private ActionKubernetesApply createAction() {
    final ActionKubernetesApply apply = new ActionKubernetesApply();
    apply.setConnectionName("conn");
    apply.setInlineManifest(MANIFEST);
    apply.setNamespace("ns1");
    apply.setWaitReady(false);
    apply.setMetadataProvider(provider);
    return apply;
  }

  @Test
  void createsResourceWhenMissing() throws Exception {
    final AtomicInteger creates = new AtomicInteger();
    server.createContext(
        "/api/v1/namespaces/ns1/configmaps",
        exchange -> {
          if ("POST".equals(exchange.getRequestMethod())) {
            creates.incrementAndGet();
            respond(exchange, 201, "{}");
          } else {
            respond(exchange, 404, "{\"code\":404}");
          }
        });
    server.createContext(
        "/api/v1/namespaces/ns1/configmaps/my-cm",
        exchange -> {
          if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 404, "{\"code\":404}");
          } else {
            respond(exchange, 404, "{\"code\":404}");
          }
        });
    final Result result = createAction().execute(new Result(), 0);
    assertTrue(result.isResult(), "create should succeed");
    assertEquals(1, creates.get(), "expected exactly one POST create");
  }

  @Test
  void patchesExistingResource() throws Exception {
    final AtomicReference<String> lastMethod = new AtomicReference<>();
    server.createContext(
        "/api/v1/namespaces/ns1/configmaps/my-cm",
        exchange -> {
          if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, MANIFEST);
          } else {
            lastMethod.set(exchange.getRequestMethod());
            respond(exchange, 200, "{}");
          }
        });
    final Result result = createAction().execute(new Result(), 0);
    assertTrue(result.isResult(), "update should succeed");
    assertEquals(
        "PATCH", lastMethod.get(), "existing resource should be patched (strategic merge)");
  }

  @Test
  void fallsBackToMergePatchOn415() throws Exception {
    final AtomicReference<String> lastContentType = new AtomicReference<>();
    server.createContext(
        "/api/v1/namespaces/ns1/configmaps/my-cm",
        exchange -> {
          if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, MANIFEST);
          } else {
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            final boolean strategic =
                "application/strategic-merge-patch+json"
                    .equals(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, strategic ? 415 : 200, "{}");
          }
        });
    final Result result = createAction().execute(new Result(), 0);
    assertTrue(result.isResult(), "should succeed after merge-patch fallback");
  }

  @Test
  void allowsForcingPut() throws Exception {
    final AtomicReference<String> lastMethod = new AtomicReference<>();
    server.createContext(
        "/api/v1/namespaces/ns1/configmaps/my-cm",
        exchange -> {
          if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, MANIFEST);
          } else {
            lastMethod.set(exchange.getRequestMethod());
            respond(exchange, 200, "{}");
          }
        });
    final ActionKubernetesApply apply = createAction();
    apply.setUpdateMode(ActionKubernetesApply.UpdateMode.PUT);
    final Result result = apply.execute(new Result(), 0);
    assertTrue(result.isResult());
    assertEquals("PUT", lastMethod.get(), "forced PUT should be used");
  }

  @Test
  void rejectedWhenNonJson() throws Exception {
    final ActionKubernetesApply apply = createAction();
    apply.setInlineManifest("not: json}");
    final Result result = apply.execute(new Result(), 0);
    assertEquals(1, result.getNrErrors());
  }

  private static void respond(
      final com.sun.net.httpserver.HttpExchange exchange, final int status, final String body)
      throws IOException {
    final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
