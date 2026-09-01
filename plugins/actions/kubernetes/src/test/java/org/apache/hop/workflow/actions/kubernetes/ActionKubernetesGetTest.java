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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.Result;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.kubernetes.metadata.KubernetesConnection;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionKubernetesGetTest {

  private static HttpServer server;
  private static int port;

  @BeforeAll
  static void init() throws Exception {
    HopLogStore.init();
    HopClientEnvironment.init();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/namespaces/ns1/pods",
        exchange -> {
          // simulate a list endpoint; the query param labelSelector decides how many items
          String body;
          final String query = exchange.getRequestURI().getQuery();
          if (query != null && query.contains("labelSelector=one")) {
            body =
                """
                {"apiVersion":"v1","kind":"PodList","items":[
                  {"apiVersion":"v1","kind":"Pod","metadata":{"name":"pod-one","namespace":"ns1"}}
                ]}
                """;
          } else {
            body =
                """
                {"apiVersion":"v1","kind":"PodList","items":[]}
                """;
          }
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes().length);
          exchange.getResponseBody().write(body.getBytes());
          exchange.close();
        });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  private MemoryMetadataProvider provider;

  @BeforeEach
  void setUp() throws Exception {
    provider = new MemoryMetadataProvider();
    final KubernetesConnection connection = new KubernetesConnection("conn");
    connection.setMaster("http://127.0.0.1:" + port);
    connection.setNamespace("ns1");
    provider.getSerializer(KubernetesConnection.class).save(connection);
  }

  @AfterEach
  void tearDown() {
    provider = null;
  }

  private ActionKubernetesGet getAction(final String labelSelector) {
    final ActionKubernetesGet get = new ActionKubernetesGet();
    get.setConnectionName("conn");
    get.setKind("Pod");
    get.setApiVersion("v1");
    get.setNamespace("ns1");
    get.setLabelSelector(labelSelector);
    get.setMetadataProvider(provider);
    return get;
  }

  @Test
  void labelSelectorSingleMatchSucceeds() throws Exception {
    final Result result = getAction("one").execute(new Result(), 0);
    assertTrue(result.isResult(), "single match should succeed");
  }

  @Test
  void labelSelectorNoMatchFails() throws Exception {
    final Result result = getAction("none").execute(new Result(), 0);
    assertFalse(result.isResult(), "no match should fail");
  }
}
