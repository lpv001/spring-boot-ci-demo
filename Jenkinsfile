
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
                    sh '''
set -eu
set +x
umask 077

SETTINGS_FILE="$(mktemp)"
trap 'rm -f "$SETTINGS_FILE"' EXIT

# Escape XML special characters in credentials.
xml_escape() {
    printf '%s' "$1" |
        sed \
            -e 's/\&/\&amp;/g' \
            -e 's/</\&lt;/g' \
            -e 's/>/\&gt;/g' \
            -e 's/"/\&quot;/g' \
            -e "s/'/\\&apos;/g"
}

READ_USER_XML="$(xml_escape "$NEXUS_READ_USER")"
READ_PASSWORD_XML="$(xml_escape "$NEXUS_READ_PASSWORD")"

# Generate a temporary Maven settings file.
cat > "$SETTINGS_FILE" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
    <mirrors>
        <mirror>
            <id>nexus-public</id>
            <name>Nexus Maven Group</name>
            <url>${NEXUS_URL}/repository/${NEXUS_GROUP_REPO}/</url>
            <mirrorOf>*</mirrorOf>
        </mirror>
    </mirrors>
    <servers>
        <server>
            <id>nexus-public</id>
            <username>${READ_USER_XML}</username>
            <password>${READ_PASSWORD_XML}</password>
        </server>
    </servers>
</settings>
EOF

echo "========== Maven Clean and Verify =========="

mvn -B \
    -s "$SETTINGS_FILE" \
    -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
    clean verify

echo "Maven tests and verification completed."
'''
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
                    sh '''
set -eu
set +x
umask 077

SETTINGS_FILE="$(mktemp)"
trap 'rm -f "$SETTINGS_FILE"' EXIT

# Escape XML special characters in credentials.
xml_escape() {
    printf '%s' "$1" |
        sed \
            -e 's/\&/\&amp;/g' \
            -e 's/</\&lt;/g' \
            -e 's/>/\&gt;/g' \
            -e 's/"/\&quot;/g' \
            -e "s/'/\\&apos;/g"
}

READ_USER_XML="$(xml_escape "$NEXUS_READ_USER")"
READ_PASSWORD_XML="$(xml_escape "$NEXUS_READ_PASSWORD")"
PUBLISH_USER_XML="$(xml_escape "$NEXUS_PUBLISH_USER")"
PUBLISH_PASSWORD_XML="$(xml_escape "$NEXUS_PUBLISH_PASSWORD")"

# Generate a temporary Maven settings file with separate read
# and publish credentials.
cat > "$SETTINGS_FILE" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
    <mirrors>
        <mirror>
            <id>nexus-public</id>
            <name>Nexus Maven Group</name>
            <url>${NEXUS_URL}/repository/${NEXUS_GROUP_REPO}/</url>
            <mirrorOf>*</mirrorOf>
        </mirror>
    </mirrors>
    <servers>
        <server>
            <id>nexus-public</id>
            <username>${READ_USER_XML}</username>
            <password>${READ_PASSWORD_XML}</password>
        </server>
        <server>
            <id>nexus-snapshots</id>
            <username>${PUBLISH_USER_XML}</username>
            <password>${PUBLISH_PASSWORD_XML}</password>
        </server>
    </servers>
</settings>
EOF

echo "========== Publish Maven Artifact =========="

mvn -B \
    -s "$SETTINGS_FILE" \
    -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
    deploy

echo "Maven deploy completed."
'''
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

echo "========== Build Summary =========="
echo "Application verification and Nexus deployment completed."
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
