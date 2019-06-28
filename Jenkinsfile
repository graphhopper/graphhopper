pipeline {
  agent {
    label "jenkins-nodejs"
    }
  environment {
    ORG = 'curefit'
    APP_NAME = 'graphhopper'
    CHARTMUSEUM_CREDS = credentials('jenkins-x-chartmuseum')
    NPM_TOKEN = credentials('npm-token')
    TEMPLATE = 'basic'
    CHART_REPOSITORY= 'http://jenkins-x-chartmuseum:8080'
    }
  stages {
    stage('Publish K8s Chart') {
      steps {
        container('nodejs') {
          script{
              gitGetCredentials()
              getChart()
              releaseChart()
            }
          }
        }
      };
    stage('Prepare Docker Image for Stage Environment') {
      when {
            branch 'stage';
        }
      environment {
        PREVIEW_VERSION = "0.0.$BUILD_NUMBER-$BRANCH_NAME".replaceAll('_','-')
        }
      steps {
        container('nodejs') {
          script{
            def URL = "${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:${PREVIEW_VERSION}"
            buildDockerfile("${APP_NAME}", URL, "stage")
            pushDockerImage(URL)
            updateArtifact("${DOCKER_REGISTRY}/${ORG}/${APP_NAME}", "${PREVIEW_VERSION}", "stage")
            }
          }
        }
      };
    stage('Prepare Docker Image for Production Environment') {
      when{
          branch 'master';
        }
      steps {
        container('nodejs') {
          script{
            gitGetCredentials()
            createGitReleaseVersion()
            def URL = "${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:\$(cat ${WORKSPACE}/VERSION)"
            buildDockerfile("${APP_NAME}", URL, "prod")
            pushDockerImage(URL)
            updateArtifact("${DOCKER_REGISTRY}/${ORG}/${APP_NAME}", "\$(cat ${WORKSPACE}/VERSION)", "prod")
            }
          }
        }
      };
    stage('Create GIT Release') {
      when{
          branch 'master';
        }
      steps {
        container('nodejs') {
          changelogAndRelease()
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
  sh "docker build -t ${tag} --build-arg TOKEN=${NPM_TOKEN} --build-arg ENVIRONMENT=${env} --build-arg APP_NAME=${appName} --network host ."
}

void pushDockerImage(tag){
   sh "docker push ${tag}"
   sh "jx step post build --image ${URL}"
}

void changelogAndRelease(){
  sh """
  jx step tag --version \$(cat VERSION) --verbose
  """
}

void gitCheckout(){
  sh "git checkout $BRANCH_NAME"
}

void gitGetCredentials(){
  sh "git config --global credential.helper store"
  sh "jx step git credentials"
}

void submodule(){
  sh "touch .gitmodules"
  sh """sed -i -e 's,git@github.com:curefit/,https://github.com/curefit/, g' .gitmodules"""
  sh "git submodule sync"
  sh "git submodule update --init --recursive"
}

void getChart(){
  sh "git clone https://github.com/curefit/k8s-templates.git"
  sh """
  cd ./k8s-templates/${TEMPLATE}
  chmod +x init.sh
  ./init.sh ${APP_NAME}
  """
  sh "rm -rf ./k8s-templates"
}

void createGitReleaseVersion(){
  sh "echo \$(jx-release-version) > ${WORKSPACE}/VERSION"
}

void releaseChart(){
  sh """
  cd ./charts/${APP_NAME}
  helm init --client-only
  helm plugin install https://github.com/chartmuseum/helm-push
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
