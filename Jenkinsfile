
import groovy.xml.XmlUtil

def writeMavenSettings(
    String outputPath,
    String nexusUrl,
    String groupRepo,
    String readUser,
    String readPassword,
    String publishUser = null,
    String publishPassword = null
) {
    def settings = new groovy.util.Node(null, 'settings', [
        xmlns: 'http://maven.apache.org/SETTINGS/1.2.0'
    ])

    def mirrors = settings.appendNode('mirrors')
    def mirror = mirrors.appendNode('mirror')
    mirror.appendNode('id', 'nexus-public')
    mirror.appendNode('name', 'Nexus Maven Group')
    mirror.appendNode('url', "${nexusUrl}/repository/${groupRepo}/")
    mirror.appendNode('mirrorOf', '*')

    def servers = settings.appendNode('servers')

    def readServer = servers.appendNode('server')
    readServer.appendNode('id', 'nexus-public')
    readServer.appendNode('username', readUser)
    readServer.appendNode('password', readPassword)

    if (publishUser != null && publishPassword != null) {
        def publishServer = servers.appendNode('server')
        publishServer.appendNode('id', 'nexus-snapshots')
        publishServer.appendNode('username', publishUser)
        publishServer.appendNode('password', publishPassword)
    }

    // Serialize XML with proper escaping.
    def xml = XmlUtil.serialize(settings)

    new File(outputPath).withWriter('UTF-8') { writer ->
        writer.write(xml)
    }
}

pipeline {
    agent any

    tools {
        maven 'Maven-3.10.0'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(
            logRotator(
                numToKeepStr: '20',
                artifactNumToKeepStr: '10'
            )
        )
    }

    environment {
        NEXUS_URL = 'http://nexus.nexus.svc.cluster.local:8081'
        NEXUS_GROUP_REPO = 'maven-public'
        NEXUS_SNAPSHOT_REPO = 'maven-snapshots'
        MAVEN_LOCAL_REPO = "${WORKSPACE}/.m2-ci"
    }

    stages {

        stage('Check Build Environment') {
            steps {
                sh '''
set -eu

echo "========== Java =========="
java -version

echo "========== Maven =========="
mvn -version

echo "========== Workspace =========="
pwd
'''

                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-reader',
                        usernameVariable: 'NEXUS_READ_USER',
                        passwordVariable: 'NEXUS_READ_PASSWORD'
                    )
                ]) {
                    sh '''
set -eu
set +x

echo "========== Nexus Connectivity =========="

curl --fail --silent --show-error \
    --user "$NEXUS_READ_USER:$NEXUS_READ_PASSWORD" \
    "$NEXUS_URL/repository/$NEXUS_GROUP_REPO/" \
    -o /dev/null

echo "Nexus authentication successful."
'''
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm

                sh '''
set -eu

echo "========== Git Commit =========="
git rev-parse HEAD

echo "========== Project Files =========="
ls -la

test -f pom.xml
test -f ci-settings.xml

echo "Project files validated."
'''
            }
        }

        stage('Validate Nexus Mirror') {
            steps {
                sh '''
set -eu

echo "========== Validate Maven Mirror =========="

grep -Eq \
    '<mirrorOf>[[:space:]]*\\*[[:space:]]*</mirrorOf>' \
    ci-settings.xml

grep -F \
    'http://nexus.nexus.svc.cluster.local:8081/repository/maven-public/' \
    ci-settings.xml

echo "OK: Maven mirror points to Nexus maven-public."
'''
            }
        }

        stage('Clean and Test') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-reader',
                        usernameVariable: 'NEXUS_READ_USER',
                        passwordVariable: 'NEXUS_READ_PASSWORD'
                    )
                ]) {
                    script {
                        def settingsPath =
                            "${pwd(tmp: true)}/maven-settings-${env.BUILD_NUMBER}.xml"

                        writeMavenSettings(
                            settingsPath,
                            env.NEXUS_URL,
                            env.NEXUS_GROUP_REPO,
                            env.NEXUS_READ_USER,
                            env.NEXUS_READ_PASSWORD
                        )

                        try {
                            sh '''
set -eu
set +x

mvn -B \
    -s "$SETTINGS_FILE" \
    -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
    clean verify
'''.replace(
                                '$SETTINGS_FILE',
                                settingsPath
                            )
                        } finally {
                            sh "rm -f '${settingsPath}'"
                        }
                    }
                }
            }

            post {
                always {
                    junit(
                        testResults: '**/target/surefire-reports/TEST-*.xml',
                        allowEmptyResults: true
                    )
                }
            }
        }

        stage('Publish to Nexus') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-reader',
                        usernameVariable: 'NEXUS_READ_USER',
                        passwordVariable: 'NEXUS_READ_PASSWORD'
                    ),
                    usernamePassword(
                        credentialsId: 'nexus-publisher',
                        usernameVariable: 'NEXUS_PUBLISH_USER',
                        passwordVariable: 'NEXUS_PUBLISH_PASSWORD'
                    )
                ]) {
                    script {
                        def settingsPath =
                            "${pwd(tmp: true)}/maven-deploy-settings-${env.BUILD_NUMBER}.xml"

                        writeMavenSettings(
                            settingsPath,
                            env.NEXUS_URL,
                            env.NEXUS_GROUP_REPO,
                            env.NEXUS_READ_USER,
                            env.NEXUS_READ_PASSWORD,
                            env.NEXUS_PUBLISH_USER,
                            env.NEXUS_PUBLISH_PASSWORD
                        )

                        try {
                            sh '''
set -eu
set +x

mvn -B \
    -s "$SETTINGS_FILE" \
    -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
    deploy
'''.replace(
                                '$SETTINGS_FILE',
                                settingsPath
                            )
                        } finally {
                            sh "rm -f '${settingsPath}'"
                        }
                    }
                }
            }
        }

        stage('Verify Artifact') {
            steps {
                sh '''
set -eu

echo "========== Generated JAR Files =========="

find . -type f -path '*/target/*.jar' \
    ! -name '*-sources.jar' \
    ! -name '*-javadoc.jar' \
    -print

echo "========== Built Commit =========="
git rev-parse HEAD
'''
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Build, tests, and Nexus publication completed.'
        }

        failure {
            echo 'FAILED: Check Console Output for the first failed stage.'
        }

        always {
            echo 'CI pipeline finished.'
        }
    }
}
