#!groovy
@Library('stuart-jenkins-pipelines') _

def masterBranch = 'master'
def developBranch = 'develop'
def project = "graphhopper-matrix"
def artifactVersion
def nexusUser
def nexusPass

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(daysToKeepStr: '20', numToKeepStr: '100'))
        timestamps()
        timeout(time: 3, unit: 'HOURS')
        ansiColor('xterm')
    }

    environment {
        JENKINS_USER_NAME = "${sh(script: 'id -un', returnStdout: true).trim()}"
        JENKINS_USER_ID = "${sh(script: 'id -u', returnStdout: true).trim()}"
        JENKINS_GROUP_ID = "${sh(script: 'id -g', returnStdout: true).trim()}"
        JENKINS_DOCKER_GROUP_ID = "${sh(script: 'getent group docker | cut -d: -f3', returnStdout: true).trim()}"
        SLACK_NOTIFICATION_CHANNEL = "dsc_jenkins"
        SLACK_ROUTE_PLANNER_NOTIFICATION_CHANNEL = "route-planner-notifications"
    }

    stages {
        stage('Container build') {

            agent {
                dockerfile {
                    filename 'Dockerfile.build'
                    additionalBuildArgs '''\
                                --build-arg GID=$JENKINS_GROUP_ID \
                                --build-arg UID=$JENKINS_USER_ID \
                                --build-arg UNAME=$JENKINS_USER_NAME \
                                '''
                    args '''\
                        --group-add $JENKINS_DOCKER_GROUP_ID \
                        -v $HOME/.ivy2/.credentials:$WORKSPACE/.credentials:ro
                        '''
                    reuseNode true
                }
            }

            stages {
                stage('Abort previous build if running') {
                    steps {
                        script {
                            common.abortPreviousRunningBuilds()
                        }
                    }
                }

                stage('Prepare environment') {
                    steps {
                        script {
                            artifactVersion = sh(returnStdout: true, script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout').trim()
                            echo "Artifact version: $artifactVersion"

                            def userLine = sh(script: "cat .credentials | grep user=", returnStdout: true).trim()
                            def userPrefix = "user="
                            nexusUser = sh(script: "echo '$userLine' | sed -e \"s/^$userPrefix//\"", returnStdout: true).trim()
                            def passLine = sh(script: "cat .credentials | grep password=", returnStdout: true).trim()
                            def passPrefix = "password="
                            nexusPass = sh(script: "echo '$passLine' | sed -e \"s/^$passPrefix//\"", returnStdout: true).trim()
                        }
                    }
                }

                stage('Deploy Notifications') {
                    environment {
                        gitLogMessage = safeRun("git log --no-merges --pretty='%h - %s (%an)' \$(git tag --sort=-version:refname | head -n 2 | tail -n 1)..HEAD")
                    }
                    when {
                        anyOf {
                            branch masterBranch;
                        }
                    }
                    steps {
                        script {
                            slackNotification("[$project] - *Release* will be generated.\n" +
                                    "This is the changelog:\n" +
                                    "```" + env.gitLogMessage + "```", SLACK_ROUTE_PLANNER_NOTIFICATION_CHANNEL, 'grey')
                        }
                    }
                }

                stage('Compile') {
                    steps {
                        sh "mvn clean compile"
                    }
                }

                stage('Style') {
                    steps {
                        sh "mvn checkstyle:check"
                    }
                }

                stage('Test') {
                    steps {
                        sh "mvn test"
                    }
                }

                stage('Publish Snapshot') {
                    when {
                        branch "develop"
                    }
                    steps {
                        script {
                            sh "mvn  -Drepo.id=stuart-maven-snapshots -Drepo.login='$nexusUser' -Drepo.pwd='$nexusPass' deploy"
                        }
                    }
                }

                stage('Publish Release') {
                    when {
                        branch "master"
                    }
                    steps {
                        script {
                            sh "mvn  -Drepo.id=stuart-maven-releases -Drepo.login='$nexusUser' -Drepo.pwd='$nexusPass' deploy"
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                if (env.BRANCH_NAME in [masterBranch, developBranch]) {
                    notifyBuild(currentBuild.result, env.SLACK_NOTIFICATION_CHANNEL)
                }
            }
        }

        failure {
            script {
                if (env.BRANCH_NAME == developBranch) {
                    slackNotification("Error generating `Snapshot` ${project} version `${artifactVersion}`: ${env.BUILD_URL}", SLACK_ROUTE_PLANNER_NOTIFICATION_CHANNEL, 'red')
                }

                if (env.BRANCH_NAME == masterBranch) {
                    slackNotification("Error generating `release` branch ${project}` version `${artifactVersion}`: ${env.BUILD_URL}", SLACK_ROUTE_PLANNER_NOTIFICATION_CHANNEL, 'red')
                }
            }
        }

        success {
            script {
                if (env.BRANCH_NAME == developBranch) {
                    slackNotification("`New Snapshot generated` ${project} version `${artifactVersion}`: ${env.BUILD_URL}", SLACK_ROUTE_PLANNER_NOTIFICATION_CHANNEL, 'green')
                }

                if (env.BRANCH_NAME == masterBranch) {
                    slackNotification("`New release generated` ${project} version `${artifactVersion}`: ${env.BUILD_URL}", SLACK_ROUTE_PLANNER_NOTIFICATION_CHANNEL, 'green')
                }
            }
        }
    }

}