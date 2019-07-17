pipeline {
  agent {
    label "k8s-slave"
    }
  environment {
    ORG = 'curefit'
    APP_NAME = 'graphhopper'
    CHARTMUSEUM_CREDS = credentials('prod-chartmuseum')
    NPM_TOKEN = credentials('npm-token')
    TEMPLATE = 'basic'
    CHART_REPOSITORY= 'http://chartmuseum.production.cure.fit.internal'     
    }
  stages {
    stage('Publish K8s Chart') {
      when { anyOf {branch 'stage'; branch 'master' } }
      steps {
          script{
              getChart()
              releaseChart()
            }
          }
      };
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

void verifyCharts(){
  sh 'helm lint charts/${APP_NAME}'
  sh 'helm init --client-only'
  sh 'helm template charts/${APP_NAME}'
}

void buildDockerfile(appName, tag, env){
  sh "sudo docker build -t ${tag} --build-arg TOKEN=${NPM_TOKEN} --build-arg ENVIRONMENT=${env} --build-arg APP_NAME=${appName} --network host ."
}

void pushDockerImage(tag){
   sh "sudo docker push ${tag}"
}

void submodule(){
  sh "touch .gitmodules"
  sh """sed -i -e 's,git@github.com:curefit/,https://github.com/curefit/, g' .gitmodules"""
  sh "git submodule sync"
  sh "git submodule update --init --recursive"
}

void getChart(){
  sh "git clone git@github.com:curefit/k8s-templates.git"
  sh """
  cd ./k8s-templates/${TEMPLATE}
  chmod +x init-k8s.sh
  ./init-k8s.sh ${APP_NAME}
  """
  sh "rm -rf ./k8s-templates"
}

void releaseChart(){
  sh """
  cd ./charts/${APP_NAME}
  helm init --client-only
  helm push . $CHART_REPOSITORY --version 1.0.0-$BRANCH_NAME --force --username ${CHARTMUSEUM_CREDS_USR} --password ${CHARTMUSEUM_CREDS_PSW}
  """
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
