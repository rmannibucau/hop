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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.Result;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionKubernetesDeleteTest {

  private HttpServer server;
  private int port;
  private MemoryMetadataProvider provider;

  @BeforeEach
  void init() throws Exception {
    HopLogStore.init();
    HopClientEnvironment.init();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();
    provider = new MemoryMetadataProvider();
    final KubernetesConnection connection = new KubernetesConnection("conn");
    connection.setMaster("http://127.0.0.1:" + port);
    connection.setNamespace("ns1");
    provider.getSerializer(KubernetesConnection.class).save(connection);
  }

  @AfterEach
  void stop() throws Exception {
    server.stop(0);
    provider = null;
  }

  private ActionKubernetesDelete createAction(final String name) {
    final ActionKubernetesDelete delete = new ActionKubernetesDelete();
    delete.setConnectionName("conn");
    delete.setKind("ConfigMap");
    delete.setNamespace("ns1");
    delete.setName(name);
    delete.setMetadataProvider(provider);
    return delete;
  }

  @Test
  void deletesByName() throws Exception {
    final AtomicInteger deletes = new AtomicInteger();
    server.createContext(
        "/api/v1/namespaces/ns1/configmaps/my-cm",
        exchange -> {
          if ("DELETE".equals(exchange.getRequestMethod())) {
            deletes.incrementAndGet();
            respond(exchange, 200, "{\"code\":200}");
          } else {
            respond(exchange, 200, "{\"kind\":\"ConfigMap\",\"metadata\":{\"name\":\"my-cm\"}}");
          }
        });
    server.start();
    final Result result = createAction("my-cm").execute(new Result(), 0);
    assertTrue(result.isResult(), "delete should succeed");
    assertTrue(deletes.get() >= 1, "expected at least one DELETE");
  }

  private static void respond(
      final com.sun.net.httpserver.HttpExchange exchange, final int status, final String body)
      throws java.io.IOException {
    final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
