#!groovy

pipeline {
    agent {
        docker { image 'maven:3.5.3-jdk-10-slim' }
    }

    stages {
        stage('Test') {
            environment {
                CODECOV_TOKEN = '5ecccbfe-730c-4a46-801d-f6e539cd97e9'
            }
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit(testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true)
                    sh 'curl -s https://codecov.io/bash | bash'
                }
            }
        }
    }
}
