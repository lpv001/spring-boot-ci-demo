
pipeline {
    agent any

    tools {
        jdk 'Java-21'
        maven 'Maven-3.10.0'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(
            numToKeepStr: '20',
            artifactNumToKeepStr: '10'
        ))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        NEXUS_URL = 'http://nexus.nexus.svc.cluster.local:8081'
        MAVEN_SETTINGS_ID = 'maven-settings-ci'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()

                git branch: 'main',
                    url: 'https://github.com/lpv001/spring-boot-ci-demo.git'

                sh '''
                    set -eu
                    test -f pom.xml
                    test -f mvnw || command -v mvn
                    echo "Repository checkout successful"
                '''
            }
        }

        stage('Validate Configuration') {
            steps {
                sh '''
                    set -eu
                    echo "Java version:"
                    java -version

                    echo "Maven version:"
                    mvn -version

                    echo "Checking Nexus connectivity..."
                    curl --fail --silent --show-error \
                        --output /dev/null \
                        "$NEXUS_URL/service/rest/v1/status"

                    echo "Nexus is reachable"
                '''
            }
        }

        stage('Build and Test') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-reader',
                        usernameVariable: 'NEXUS_READER_USR',
                        passwordVariable: 'NEXUS_READER_PSW'
                    ),
                    usernamePassword(
                        credentialsId: 'nexus-publisher',
                        usernameVariable: 'NEXUS_PUBLISHER_USR',
                        passwordVariable: 'NEXUS_PUBLISHER_PSW'
                    )
                ]) {
                    configFileProvider([
                        configFile(
                            fileId: "${MAVEN_SETTINGS_ID}",
                            variable: 'MAVEN_SETTINGS'
                        )
                    ]) {
                        sh '''
                            set -eu
                            mvn -B -ntp \
                                -s "$MAVEN_SETTINGS" \
                                clean verify
                        '''
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Publish Snapshot to Nexus') {
            when {
                expression {
                    return env.BRANCH_NAME == null ||
                           env.BRANCH_NAME == 'main'
                }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-reader',
                        usernameVariable: 'NEXUS_READER_USR',
                        passwordVariable: 'NEXUS_READER_PSW'
                    ),
                    usernamePassword(
                        credentialsId: 'nexus-publisher',
                        usernameVariable: 'NEXUS_PUBLISHER_USR',
                        passwordVariable: 'NEXUS_PUBLISHER_PSW'
                    )
                ]) {
                    configFileProvider([
                        configFile(
                            fileId: "${MAVEN_SETTINGS_ID}",
                            variable: 'MAVEN_SETTINGS'
                        )
                    ]) {
                        sh '''
                            set -eu
                            mvn -B -ntp \
                                -s "$MAVEN_SETTINGS" \
                                deploy
                        '''
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'CI pipeline completed successfully.'
        }
        failure {
            echo 'CI pipeline failed. Check the stage logs.'
        }
        always {
            archiveArtifacts(
                artifacts: '**/target/*.jar',
                allowEmptyArchive: true,
                fingerprint: true
            )
        }
    }
}
