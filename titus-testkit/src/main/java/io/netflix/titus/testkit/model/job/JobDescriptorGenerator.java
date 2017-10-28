/*
 * Copyright 2017 Netflix, Inc.
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

package io.netflix.titus.testkit.model.job;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import io.netflix.titus.api.jobmanager.model.job.Capacity;
import io.netflix.titus.api.jobmanager.model.job.Image;
import io.netflix.titus.api.jobmanager.model.job.JobDescriptor;
import io.netflix.titus.api.jobmanager.model.job.JobModel;
import io.netflix.titus.api.jobmanager.model.job.Owner;
import io.netflix.titus.api.jobmanager.model.job.ext.BatchJobExt;
import io.netflix.titus.api.jobmanager.model.job.ext.ServiceJobExt;
import io.netflix.titus.api.jobmanager.model.job.migration.MigrationPolicy;
import io.netflix.titus.api.jobmanager.model.job.retry.RetryPolicy;
import io.netflix.titus.common.data.generator.DataGenerator;
import io.netflix.titus.testkit.model.PrimitiveValueGenerators;

import static io.netflix.titus.common.data.generator.DataGenerator.items;
import static io.netflix.titus.common.data.generator.DataGenerator.union;

/**
 */
public final class JobDescriptorGenerator {

    private JobDescriptorGenerator() {
    }

    public static DataGenerator<Owner> owners() {
        return PrimitiveValueGenerators.emailAddresses().map(ea -> JobModel.newOwner().withTeamEmail(ea).build());
    }

    public static DataGenerator<RetryPolicy> retryPolicies() {
        return DataGenerator.items(JobModel.newDelayedRetryPolicy().withDelay(100, TimeUnit.MILLISECONDS).withRetries(5).build());
    }

    public static DataGenerator<String> capacityGroups() {
        return DataGenerator.items("flex1", "flex2", "critical1", "critical2");
    }

    public static DataGenerator<BatchJobExt> batchJobExtensions() {
        return retryPolicies().map(retryPolicy -> JobModel.newBatchJobExt()
                .withSize(1)
                .withRuntimeLimitMs(60_000)
                .withRetryPolicy(retryPolicy)
                .build()
        );
    }

    public static DataGenerator<ServiceJobExt> serviceJobExtensions() {
        return retryPolicies().map(retryPolicy -> JobModel.newServiceJobExt()
                .withCapacity(JobModel.newCapacity().withMin(0).withDesired(1).withMax(2).build())
                .withRetryPolicy(retryPolicy)
                .build()
        );
    }

    public static DataGenerator<JobDescriptor<BatchJobExt>> batchJobDescriptors() {
        DataGenerator<JobDescriptor.Builder<BatchJobExt>> withOwner = union(
                items(JobModel.<BatchJobExt>newJobDescriptor()),
                owners(),
                (builder, owners) -> builder.but().withOwner(owners)
        );
        DataGenerator<JobDescriptor.Builder<BatchJobExt>> withJobGroupInfo = union(
                withOwner,
                DataGenerator.items(JobModel.newJobGroupInfo().withStack("").withDetail("").withSequence("").build()),
                (builder, jobGroupInfos) -> builder.but().withJobGroupInfo(jobGroupInfos)
        );
        DataGenerator<JobDescriptor.Builder<BatchJobExt>> withContainer = union(
                withJobGroupInfo,
                ContainersGenerator.containers(),
                (builder, container) -> builder.but().withContainer(container)
        );
        DataGenerator<JobDescriptor.Builder<BatchJobExt>> withCapacityGroup = union(
                withContainer,
                capacityGroups(),
                (builder, capacityGroup) -> builder.but().withCapacityGroup(capacityGroup)
        );
        DataGenerator<JobDescriptor.Builder<BatchJobExt>> withAppName = union(
                withCapacityGroup,
                PrimitiveValueGenerators.applicationNames(),
                (builder, applicationName) -> builder.but().withApplicationName(applicationName)
        );
        DataGenerator<JobDescriptor.Builder<BatchJobExt>> withExtensions = union(
                withAppName,
                batchJobExtensions(),
                (builder, batchJobExt) -> builder.but().withExtensions(batchJobExt)
        );
        return withExtensions.map(builder -> builder.withAttributes(Collections.singletonMap("labelA", "valueA")).build());
    }

    public static DataGenerator<JobDescriptor<ServiceJobExt>> serviceJobDescriptors() {
        DataGenerator<JobDescriptor.Builder<ServiceJobExt>> withOwner = union(
                items(JobModel.<ServiceJobExt>newJobDescriptor()),
                owners(),
                (builder, owners) -> builder.but().withOwner(owners)
        );
        DataGenerator<JobDescriptor.Builder<ServiceJobExt>> withJobGroupInfo = union(
                withOwner,
                JobClustersGenerator.jobGroupInfos(),
                (builder, jobGroupInfos) -> builder.but().withJobGroupInfo(jobGroupInfos)
        );
        DataGenerator<JobDescriptor.Builder<ServiceJobExt>> withContainer = union(
                withJobGroupInfo,
                ContainersGenerator.containers().map(c -> c.but(cc -> cc.getContainerResources().toBuilder().withAllocateIP(true).build())),
                (builder, container) -> builder.but().withContainer(container)
        );
        DataGenerator<JobDescriptor.Builder<ServiceJobExt>> withCapacityGroup = union(
                withContainer,
                capacityGroups(),
                (builder, capacityGroup) -> builder.but().withCapacityGroup(capacityGroup)
        );
        DataGenerator<JobDescriptor.Builder<ServiceJobExt>> withAppName = union(
                withCapacityGroup,
                PrimitiveValueGenerators.applicationNames(),
                (builder, applicationName) -> builder.but().withApplicationName(applicationName)
        );
        DataGenerator<JobDescriptor.Builder<ServiceJobExt>> withExtensions = union(
                withAppName,
                serviceJobExtensions(),
                (builder, serviceJobExt) -> builder.but().withExtensions(serviceJobExt)
        );
        return withExtensions.map(builder -> builder.withAttributes(Collections.singletonMap("labelA", "valueA")).build());
    }

    public static JobDescriptor<BatchJobExt> oneTaskBatchJobDescriptor() {
        JobDescriptor<BatchJobExt> jobDescriptor = batchJobDescriptors().getValue();
        Image imageWithTag = JobModel.newImage().withName("titusops/echo").withTag("latest").build();
        return JobModel.newJobDescriptor(jobDescriptor)
                .withContainer(JobModel.newContainer(jobDescriptor.getContainer()).withImage(imageWithTag).build())
                .withExtensions(JobModel.newBatchJobExt(jobDescriptor.getExtensions())
                        .withSize(1)
                        .withRetryPolicy(JobModel.newImmediateRetryPolicy().withRetries(0).build())
                        .build()
                )
                .build();
    }

    public static JobDescriptor<ServiceJobExt> oneTaskServiceJobDescriptor() {
        JobDescriptor<ServiceJobExt> jobDescriptor = serviceJobDescriptors().getValue();
        Image imageWithTag = JobModel.newImage().withName("titusops/echo").withTag("latest").build();
        return JobModel.newJobDescriptor(jobDescriptor)
                .withContainer(JobModel.newContainer(jobDescriptor.getContainer()).withImage(imageWithTag).build())
                .withExtensions(JobModel.newServiceJobExt(jobDescriptor.getExtensions())
                        .withCapacity(Capacity.newBuilder().withMin(0).withDesired(1).withMax(2).build())
                        .withRetryPolicy(JobModel.newImmediateRetryPolicy().withRetries(0).build())
                        .withMigrationPolicy(JobModel.newDefaultMigrationPolicy().build())
                        .build()
                )
                .build();
    }
}
