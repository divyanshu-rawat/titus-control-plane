/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.titus.master.mesos.kubeapiserver;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.titus.common.annotation.Experimental;
import com.netflix.titus.common.runtime.TitusRuntime;
import com.netflix.titus.common.util.code.CodeInvariants;
import com.netflix.titus.common.util.guice.annotation.Activator;
import com.netflix.titus.common.util.guice.annotation.Deactivator;
import com.netflix.titus.master.mesos.MesosConfiguration;
import com.netflix.titus.master.mesos.kubeapiserver.model.v1.V1OpportunisticResource;
import com.netflix.titus.master.mesos.kubeapiserver.model.v1.V1OpportunisticResourceList;
import com.netflix.titus.master.scheduler.opportunistic.OpportunisticCpuAvailability;
import com.netflix.titus.master.scheduler.opportunistic.OpportunisticCpuAvailabilityProvider;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.util.CallGeneratorParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.titus.master.mesos.kubeapiserver.KubeUtil.createApiClient;
import static com.netflix.titus.master.mesos.kubeapiserver.KubeUtil.createSharedInformerFactory;

@Experimental(detail = "Informer-pattern based integration with Kubernetes for opportunistic resources", deadline = "12/1/2019")
@Singleton
public class KubeOpportunisticResourceProvider implements OpportunisticCpuAvailabilityProvider {
    private static final Logger logger = LoggerFactory.getLogger(KubeOpportunisticResourceProvider.class);
    private static final String CLIENT_METRICS_PREFIX = "titusMaster.mesos.kubeOpportunisticResourceProvider";

    private static final String OPPORTUNISTIC_RESOURCE_GROUP = "titus.netflix.com";
    private static final String OPPORTUNISTIC_RESOURCE_VERSION = "v1";
    private static final String OPPORTUNISTIC_RESOURCE_NAMESPACE = "default";
    private static final String OPPORTUNISTIC_RESOURCE_PLURAL = "opportunistic-resources";

    private final CustomObjectsApi api;
    private final TitusRuntime titusRuntime;
    private final SharedIndexInformer<V1OpportunisticResource> informer;
    private final SharedInformerFactory informerFactory;
    private final Id cpuSupplyId;
    private final ScheduledExecutorService metricsPollerExecutor;

    private static long currentOpportunisticCpuCount(KubeOpportunisticResourceProvider self) {
        return self.getOpportunisticCpus().values().stream()
                .filter(self::isNotExpired)
                .mapToLong(OpportunisticCpuAvailability::getCount)
                .sum();
    }

    @Inject
    public KubeOpportunisticResourceProvider(MesosConfiguration configuration, TitusRuntime titusRuntime) {
        this.titusRuntime = titusRuntime;
        cpuSupplyId = titusRuntime.getRegistry().createId("titusMaster.opportunistic.supply.cpu");

        long refreshIntervalMs = configuration.getKubeOpportunisticRefreshIntervalMs();

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("kube-opportunistic-cpu-metrics-poller")
                .setDaemon(true)
                .build();
        metricsPollerExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        PolledMeter.using(titusRuntime.getRegistry())
                .withId(cpuSupplyId)
                .scheduleOn(metricsPollerExecutor)
                .monitorValue(this, KubeOpportunisticResourceProvider::currentOpportunisticCpuCount);

        ApiClient apiClient = createApiClient(configuration.getKubeApiServerUrl(), CLIENT_METRICS_PREFIX,
                titusRuntime, 0L);
        api = new CustomObjectsApi(apiClient);

        informerFactory = createSharedInformerFactory(
                KubeOpportunisticResourceProvider.class.getSimpleName() + "-",
                apiClient
        );
        // TODO(fabio): enhance the kube client to support custom JSON deserialization options
        informer = informerFactory.sharedIndexInformerFor(
                this::listOpportunisticResourcesCall,
                V1OpportunisticResource.class,
                V1OpportunisticResourceList.class,
                refreshIntervalMs
        );

        // TODO(fabio): metrics on available opportunistic resources
        informer.addEventHandler(new ResourceEventHandler<V1OpportunisticResource>() {
            @Override
            public void onAdd(V1OpportunisticResource resource) {
                logger.info("New opportunistic resources available: instance {}, expires at {}, cpus {}, name {}",
                        resource.getInstanceId(), resource.getEnd(), resource.getCpus(), resource.getName());
            }

            @Override
            public void onUpdate(V1OpportunisticResource old, V1OpportunisticResource update) {
                logger.info("Opportunistic resources update: instance {}, expires at {}, cpus {}, name {}",
                        update.getInstanceId(), update.getEnd(), update.getCpus(), update.getName());
            }

            @Override
            public void onDelete(V1OpportunisticResource resource, boolean deletedFinalStateUnknown) {
                if (deletedFinalStateUnknown) {
                    logger.info("Stale opportunistic resource deleted, updates for it may have been missed in the past: instance {}, resource {}, name {}",
                            resource.getInstanceId(), resource.getUid(), resource.getName());
                } else {
                    logger.debug("Opportunistic resource GCed: instance {}, resource {}, name {}",
                            resource.getInstanceId(), resource.getUid(), resource.getName());
                }
            }
        });
    }

    @Activator
    public void enterActiveMode() {
        informerFactory.startAllRegisteredInformers();
    }

    @Deactivator
    public void shutdown() {
        PolledMeter.remove(titusRuntime.getRegistry(), cpuSupplyId);
        metricsPollerExecutor.shutdown();
        informerFactory.stopAllRegisteredInformers();
    }

    private Call listOpportunisticResourcesCall(CallGeneratorParams params) {
        try {
            return api.listNamespacedCustomObjectCall(
                    OPPORTUNISTIC_RESOURCE_GROUP,
                    OPPORTUNISTIC_RESOURCE_VERSION,
                    OPPORTUNISTIC_RESOURCE_NAMESPACE,
                    OPPORTUNISTIC_RESOURCE_PLURAL,
                    null,
                    null,
                    null,
                    params.resourceVersion,
                    params.timeoutSeconds,
                    params.watch,
                    null,
                    null
            );
        } catch (ApiException e) {
            codeInvariants().unexpectedError("Error building a kube http call for opportunistic resources", e);
        }
        // this should never happen, if it does the code building request calls is wrong
        return null;
    }

    private CodeInvariants codeInvariants() {
        return titusRuntime.getCodeInvariants();
    }

    private static final Comparator<OpportunisticCpuAvailability> EXPIRES_AT_COMPARATOR = Comparator
            .comparing(OpportunisticCpuAvailability::getExpiresAt);

    /**
     * Assumptions:
     * <ul>
     *     <li>Each opportunistic resource (entity) is associated with a particular agent Node, and their validity
     *     window (start to end timestamps) never overlap.</li>
     *     <li>The current active opportunistic resource (window) is always the latest to expire.</li>
     *     <li>There are no resources that will only be available in the future, i.e.: <tt>start <= now.</tt></li>
     *     <li>Tasks can only be assigned to opportunistic CPUs if their availability window hasn't yet expired.</li>
     *     <li>Opportunistic resource entities express their availability as timestamps. We ignore clock skew issues
     *     here and will handle them elsewhere. We also assume a small risk of a few tasks being allocated to expired
     *     windows, or windows expiring too soon when timestamps from these resources are skewed in relation to the
     *     master (active leader) clock.</li>
     * </ul>
     *
     * @return the active opportunistic resources availability for each agent node
     */
    @Override
    public Map<String, OpportunisticCpuAvailability> getOpportunisticCpus() {
        return informer.getIndexer().list().stream().collect(Collectors.toMap(
                V1OpportunisticResource::getInstanceId,
                resource -> new OpportunisticCpuAvailability(resource.getUid(), resource.getEnd(), resource.getCpus()),
                BinaryOperator.maxBy(EXPIRES_AT_COMPARATOR)
        ));
    }

    private boolean isNotExpired(OpportunisticCpuAvailability availability) {
        return !titusRuntime.getClock().isPast(availability.getExpiresAt().toEpochMilli());
    }

}
