/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.front50.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class StartPipelineTask implements Task {

  @Autowired
  Front50Service front50Service

  @Autowired
  DependentPipelineStarter dependentPipelineStarter

  @Override
  TaskResult execute(Stage stage) {

    String application = stage.context.pipelineApplication ?: stage.context.application
    String pipelineId = stage.context.pipelineId ?: stage.context.pipeline
    Boolean isStrategy = stage.context.pipelineParameters?.strategy ?: false

    List pipelines = isStrategy ? front50Service.getStrategies(application) : front50Service.getPipelines(application)
    Map pipelineConfig = pipelines.find { it.id == pipelineId }

    def parameters = stage.context.pipelineParameters ?: [:]

    if (isStrategy) {
      def deploymentDetails = stage.context.deploymentDetails?.collect { Map it ->
        def base = [ami: it.ami, imageName: it.imageName, imageId: it.imageId]
        if (it.region) {
          base.region = it.region
        } else if (it.zone) {
          base.zone = it.zone
        }
        return base
      } ?: [:]

      if (deploymentDetails) {
        parameters.deploymentDetails = deploymentDetails
        if (!parameters.amiName && (parameters.region || parameters.zone)) {
          def details = deploymentDetails.find { (it.region && it.region == parameters.region) ||
                                                 (it.zone && it.zone == parameters.zone) }
          if (details) {
            parameters.amiName = details.ami
            parameters.imageId = details.imageId
          }
        }
      }
    }

    def pipeline = dependentPipelineStarter.trigger(pipelineConfig, stage.context.user, stage.execution, parameters)

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [executionId: pipeline.id, executionName: pipelineConfig.name])

  }

}
