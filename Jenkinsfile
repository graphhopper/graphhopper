pipeline {
  agent {
    label "k8s-slave"
    }
  environment {
    ORG = 'curefit'
    APP_NAME = 'graphhopper'
    }
  stages {
    stage('Prepare Docker Image for Stage Environment') {
      when { branch 'stage' }
      environment {
        PREVIEW_VERSION = "$BUILD_NUMBER-$BRANCH_NAME".replaceAll('_','-')
        }
      steps {
          script{
            def URL = "${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:${PREVIEW_VERSION}"
            buildDockerfile("${APP_NAME}", URL, "stage")
            pushDockerImage(URL)
            updateArtifact("${DOCKER_REGISTRY}/${ORG}/${APP_NAME}", "${PREVIEW_VERSION}", "stage")
          }
        }
      };
    stage('Prepare Docker Image for Alpha Environment') {
          when { branch 'alpha' }
          environment {
            VERSION = "$BUILD_NUMBER-$BRANCH_NAME".replaceAll('_','-')
            }
          steps {
              script{
                def URL = "${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:${VERSION}"
                buildDockerfile("${APP_NAME}", URL, "prod")
                pushDockerImage(URL)
                updateArtifact("${DOCKER_REGISTRY}/${ORG}/${APP_NAME}", "${VERSION}", "prod")
              }
            }
          };
    stage('Prepare Docker Image for Production Environment') {
      when{ branch 'master'; }
      environment {
        RELEASE_VERSION = "$BUILD_NUMBER"
        }
      steps {
          script{
            def URL = "${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:${RELEASE_VERSION}"
            buildDockerfile("${APP_NAME}", URL, "prod")
            pushDockerImage(URL)
            updateArtifact("${DOCKER_REGISTRY}/${ORG}/${APP_NAME}", "${RELEASE_VERSION}", "prod")
            }
        }
      };
    }
  post {
    success {
      cleanWs()
      }
    }
  }

void buildDockerfile(appName, tag, env){
  sh "sudo docker build -t ${tag} --build-arg TOKEN=${NPM_TOKEN} --build-arg ENVIRONMENT=${env} --build-arg APP_NAME=${appName} --network host ."
}

void pushDockerImage(tag){
   sh "sudo docker push ${tag}"
}

void updateArtifact(repo, tag, env) {
    sh """
    touch build.properties
    echo repo=${repo} >> build.properties
    echo tag=${tag} >> build.properties
    echo env=${env} >> build.properties
    """
    archiveArtifacts 'build.properties'
}