/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.titus.api.jobmanager;

/**
 * Constant keys for Job attributes. Attributes that begin with <b>titus.</b> are readonly system generated attributes
 * while attributes that begin with <b>titusParameter.</b> are user supplied parameters.
 */
public final class JobAttributes {

    public static final String TITUS_ATTRIBUTE_PREFIX = "titus.";
    public static final String TITUS_PARAMETER_ATTRIBUTE_PREFIX = "titusParameter.";
    public static final String JOB_ATTRIBUTE_SANITIZATION_PREFIX = TITUS_ATTRIBUTE_PREFIX + "sanitization.";
    public static final String PREDICTION_ATTRIBUTE_PREFIX = TITUS_ATTRIBUTE_PREFIX + "runtimePrediction.";

    // Job Descriptor Attributes

    /**
     * Job creator.
     */
    public static final String JOB_ATTRIBUTES_CREATED_BY = TITUS_ATTRIBUTE_PREFIX + "createdBy";

    /**
     * Federated stack name. All cells under the same federated stack must share the same value.
     */
    public static final String JOB_ATTRIBUTES_STACK = TITUS_ATTRIBUTE_PREFIX + "stack";

    /**
     * Unique Cell name for a deployment that the Job has been assigned to.
     */
    public static final String JOB_ATTRIBUTES_CELL = TITUS_ATTRIBUTE_PREFIX + "cell";

    /**
     * Set to true when sanitization for iam roles fails open
     */
    public static final String JOB_ATTRIBUTES_SANITIZATION_SKIPPED_IAM = JOB_ATTRIBUTE_SANITIZATION_PREFIX + "skipped.iam";

    /**
     * Set to true when sanitization for container images (digest) fails open
     */
    public static final String JOB_ATTRIBUTES_SANITIZATION_SKIPPED_IMAGE = JOB_ATTRIBUTE_SANITIZATION_PREFIX + "skipped.image";

    /**
     * Set to true when job runtime prediction fails open
     */
    public static final String JOB_ATTRIBUTES_SANITIZATION_SKIPPED_RUNTIME_PREDICTION = JOB_ATTRIBUTE_SANITIZATION_PREFIX + "skipped.runtimePrediction";

    /**
     * Information about the prediction selector used in the evaluation process.
     */
    public static final String JOB_ATTRIBUTES_RUNTIME_PREDICTION_SELECTOR_INFO = PREDICTION_ATTRIBUTE_PREFIX + "selectorInfo";

    /**
     * Predicted runtime for a particular job in seconds, used in opportunistic CPU scheduling
     */
    public static final String JOB_ATTRIBUTES_RUNTIME_PREDICTION_SEC = PREDICTION_ATTRIBUTE_PREFIX + "predictedRuntimeSec";

    /**
     * Confidence of the predicted runtime for a particular job
     */
    public static final String JOB_ATTRIBUTES_RUNTIME_PREDICTION_CONFIDENCE = PREDICTION_ATTRIBUTE_PREFIX + "confidence";

    /**
     * Metadata (identifier) about what model generated predictions for a particular job
     */
    public static final String JOB_ATTRIBUTES_RUNTIME_PREDICTION_MODEL_ID = PREDICTION_ATTRIBUTE_PREFIX + "modelId";

    /**
     * Metadata (version) of the service that generated predictions for a particular job
     */
    public static final String JOB_ATTRIBUTES_RUNTIME_PREDICTION_VERSION = PREDICTION_ATTRIBUTE_PREFIX + "version";

    /**
     * All available runtime predictions with their associated confidence, as returned from the predictions service for
     * a particular job.
     * <p>
     * These are informational, if any particular prediction is selected for a job, it will be reflected by
     * {@link JobAttributes#JOB_ATTRIBUTES_RUNTIME_PREDICTION_SEC} and {@link JobAttributes#JOB_ATTRIBUTES_RUNTIME_PREDICTION_CONFIDENCE}.
     */
    public static final String JOB_ATTRIBUTES_RUNTIME_PREDICTION_AVAILABLE = PREDICTION_ATTRIBUTE_PREFIX + "available";

    /**
     * AB test metadata associated with a prediction.
     */
    public static final String JOB_ATTRIBUTES_RUNTIME_PREDICTION_AB_TEST = PREDICTION_ATTRIBUTE_PREFIX + "abTest";

    /**
     * Allow jobs to completely opt-out of having their runtime automatically predicted during admission
     */
    public static final String JOB_PARAMETER_SKIP_RUNTIME_PREDICTION = TITUS_PARAMETER_ATTRIBUTE_PREFIX + "runtimePrediction.skip";

    /**
     * (Experimental) allow jobs to request being placed into a particular cell (affinity)
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_CELL_REQUEST = TITUS_PARAMETER_ATTRIBUTE_PREFIX + "cell.request";

    /**
     * (Experimental) allow jobs to request not being placed into a comma separated list of cell names (anti affinity)
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_CELL_AVOID = TITUS_PARAMETER_ATTRIBUTE_PREFIX + "cell.avoid";

    /**
     * A comma separated list specifying which taints this job can tolerate during scheduling. This job must be able to tolerate
     * all taints in order to be scheduled on that machine.
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_TOLERATIONS = TITUS_PARAMETER_ATTRIBUTE_PREFIX + "tolerations";

    // Container Attributes

    /**
     * Allow the task to use more CPU (as based on time slicing) than specified.
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_ALLOW_CPU_BURSTING =
            TITUS_PARAMETER_ATTRIBUTE_PREFIX + "agent.allowCpuBursting";

    /**
     * Allow the task to use more Network (as based on time and bandwidth slicing) than specified.
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_ALLOW_NETWORK_BURSTING =
            TITUS_PARAMETER_ATTRIBUTE_PREFIX + "agent.allowNetworkBursting";

    /**
     * Sets SCHED_BATCH -- Linux batch scheduling, for cache-friendly handling of lowprio, batch-like, CPU-bound, 100% non-interactive tasks.
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_SCHED_BATCH =
            TITUS_PARAMETER_ATTRIBUTE_PREFIX + "agent.schedBatch";

    /**
     * Allows the creation of nested containers.
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_ALLOW_NESTED_CONTAINERS =
            TITUS_PARAMETER_ATTRIBUTE_PREFIX + "agent.allowNestedContainers";

    /**
     * How long to wait for a task (container) to exit on its own after sending a SIGTERM -- after this period elapses, a SIGKILL will sent.
     */
    public static final String JOB_PARAMETER_ATTRIBUTES_KILL_WAIT_SECONDS =
            TITUS_PARAMETER_ATTRIBUTE_PREFIX + "agent.killWaitSeconds";

    private JobAttributes() {
    }
}
