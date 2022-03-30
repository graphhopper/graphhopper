#!/usr/bin/env groovy

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

env.GIT_DEFAULT_BRANCH = 'k8s'
final String developBranch = env.GIT_DEFAULT_BRANCH

boolean cleanDockerCompose = false
boolean doDeploy = false

String inputUrl = "${env.BUILD_URL}input/"

def scmVars

if (BRANCH_NAME != developBranch) stopOlderBuilds()

properties(BRANCH_NAME == developBranch ? [disableConcurrentBuilds()] : [])

pipeline {
  agent {
    label "standard"
  }

  options {
    parallelsAlwaysFailFast()
    timeout(time: 60, unit: 'MINUTES')
  }

  environment {
    REGISTRY_TARGET = 'bbc-registry'
  }

  stages {
    stage('scm') {
      steps{
        script{
          scmVars = checkout scm
        }
      }
    }

    stage('Build container images') {
      steps {
        dir("docker") {
          sh("LOCAL_TAG=lelab_graphhopper bbc dockerfile build test")
        }
      }
    }

    stage('Push container images') {
      when {
        expression { env.BRANCH_IS_PRIMARY }
      }
      steps {
        dir("docker") {
          sh('bbc dockerfile push')
        }
      }
    }

    stage('Ça ship là!') {
      when {
        allOf {
          branch developBranch
        }
      }
      steps {
        deploy env: 'infra-prod',
           namespace: 'lelab',
           helmRelease: "graphhopper",
           dockerImage: readFile(file: "docker//.built"),
           askConfirmation: true,
           slackChannel: '#lelab_builds'
      }
    }
  }
}
