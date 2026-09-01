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

package org.apache.hop.kubernetes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yupiik.fusion.json.JsonMapper;
import java.util.List;
import org.apache.hop.kubernetes.util.KubernetesUtil;
import org.junit.jupiter.api.Test;

class KubernetesModelsTest {

  @Test
  void bindsObjectListWithStatusConditions() {
    final String json =
        """
        {"apiVersion":"apps/v1","kind":"DeploymentList","items":[
          {"apiVersion":"apps/v1","kind":"Deployment",
           "metadata":{"name":"web","namespace":"default","labels":{"app":"web"}},
           "status":{"replicas":3,"readyReplicas":3,"observedGeneration":7,
                      "conditions":[{"type":"Available","status":"True"}]}}
        ]}
        """;
    try (final JsonMapper mapper = KubernetesUtil.createMapper()) {
      final KubernetesObjectList list = mapper.fromString(KubernetesObjectList.class, json);
      assertNotNull(list.items());
      assertEquals(1, list.items().size());
      final KubernetesObject object = list.items().get(0);
      assertEquals("web", object.metadata().name());
      assertEquals("default", object.metadata().namespace());
      assertEquals("web", object.metadata().labels().get("app"));
      final KubernetesStatus status = object.status();
      assertEquals(3, status.replicas());
      assertEquals(3, status.readyReplicas());
      assertEquals(7, status.observedGeneration());
      assertNotNull(status.conditions());
      assertEquals("Available", status.conditions().get(0).type());
      assertEquals("True", status.conditions().get(0).status());
    }
  }

  @Test
  void mapsMissingStatusMembersToNull() {
    final String json =
        """
        {"apiVersion":"v1","kind":"Pod","metadata":{"name":"p1"}}
        """;
    try (final JsonMapper mapper = KubernetesUtil.createMapper()) {
      final KubernetesObject object = mapper.fromString(KubernetesObject.class, json);
      assertNull(object.status());
      assertEquals("p1", object.metadata().name());
      assertTrue(object.other() == null || object.other().isEmpty());
    }
  }

  @Test
  void serializesBackVersionsFromDiscovery() {
    final ApiResourceList list =
        new ApiResourceList(
            "apps/v1",
            "APIResourceList",
            "v1",
            List.of(new ApiResource("deployments", "", true, "Deployment", List.of("get"))));
    try (final JsonMapper mapper = KubernetesUtil.createMapper()) {
      final String json = mapper.toString(list);
      assertTrue(json.contains("\"deployments\""));
      assertTrue(json.contains("\"Deployment\""));
    }
  }

  @Test
  void serializesJobViaModel() {
    final KubernetesJob job =
        KubernetesJob.of(
            "run-1",
            "ns1",
            new KubernetesJobSpec(
                2,
                1,
                0,
                60,
                null,
                new KubernetesPodTemplate(
                    new KubernetesPodSpec(
                        "Never",
                        List.of(
                            new KubernetesContainer(
                                "main",
                                "busybox:1.36",
                                List.of("sh", "-c"),
                                List.of("echo hi")))))));
    try (final JsonMapper mapper = KubernetesUtil.createMapper()) {
      final String json = mapper.toString(job);
      assertTrue(json.contains("\"apiVersion\":\"batch/v1\""));
      assertTrue(json.contains("\"kind\":\"Job\""));
      assertTrue(json.contains("\"name\":\"run-1\""));
      assertTrue(json.contains("\"namespace\":\"ns1\""));
      assertTrue(json.contains("\"completions\":2"));
      assertTrue(json.contains("\"image\":\"busybox:1.36\""));
      assertTrue(json.contains("\"restartPolicy\":\"Never\""));
      assertFalse(json.contains("activeDeadlineSeconds"));

      final KubernetesJob roundTrip = mapper.fromString(KubernetesJob.class, json);
      assertEquals("run-1", roundTrip.metadata().name());
      assertEquals("ns1", roundTrip.metadata().namespace());
      assertEquals(2, roundTrip.spec().completions());
      assertEquals("main", roundTrip.spec().template().spec().containers().get(0).name());
    }
  }
}
