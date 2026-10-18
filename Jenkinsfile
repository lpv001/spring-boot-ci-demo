
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
'''
            }
        }

        stage('Validate Nexus Mirror') {
            steps {
                sh '''
set -eu

python3 -c '
import xml.etree.ElementTree as ET

root = ET.parse("ci-settings.xml").getroot()
ns = {"m": root.tag.split("}")[0].strip("{")}

mirrors = root.findall("m:mirrors/m:mirror", ns)

valid = any(
    mirror.findtext("m:mirrorOf", default="", namespaces=ns) == "*"
    and mirror.findtext("m:url", default="", namespaces=ns).rstrip("/").endswith(
        "/repository/maven-public"
    )
    for mirror in mirrors
)

if not valid:
    raise SystemExit(
        "ERROR: ci-settings.xml must mirror * to Nexus maven-public"
    )

print("OK: Maven mirror points to Nexus maven-public")
'
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

SETTINGS_FILE="$(mktemp)"
chmod 600 "$SETTINGS_FILE"
trap 'rm -f "$SETTINGS_FILE"' EXIT

python3 - "$WORKSPACE/ci-settings.xml" "$SETTINGS_FILE" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

source, destination = sys.argv[1], sys.argv[2]
tree = ET.parse(source)
root = tree.getroot()
namespace = root.tag.split("}")[0].strip("{")
tag = lambda name: "{%s}%s" % (namespace, name)

servers = root.find(tag("servers"))
if servers is None:
    servers = ET.SubElement(root, tag("servers"))

server = ET.SubElement(servers, tag("server"))
ET.SubElement(server, tag("id")).text = "nexus-public"
ET.SubElement(server, tag("username")).text = os.environ["NEXUS_READ_USER"]
ET.SubElement(server, tag("password")).text = os.environ["NEXUS_READ_PASSWORD"]

tree.write(destination, encoding="UTF-8", xml_declaration=True)
PY

echo "========== Maven Clean and Verify =========="

mvn -B \
  -s "$SETTINGS_FILE" \
  -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
  clean verify
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

SETTINGS_FILE="$(mktemp)"
chmod 600 "$SETTINGS_FILE"
trap 'rm -f "$SETTINGS_FILE"' EXIT

python3 - "$WORKSPACE/ci-settings.xml" "$SETTINGS_FILE" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

source, destination = sys.argv[1], sys.argv[2]
tree = ET.parse(source)
root = tree.getroot()
namespace = root.tag.split("}")[0].strip("{")
tag = lambda name: "{%s}%s" % (namespace, name)

servers = root.find(tag("servers"))
if servers is None:
    servers = ET.SubElement(root, tag("servers"))

credentials = [
    (
        "nexus-public",
        "NEXUS_READ_USER",
        "NEXUS_READ_PASSWORD",
    ),
    (
        "nexus-snapshots",
        "NEXUS_PUBLISH_USER",
        "NEXUS_PUBLISH_PASSWORD",
    ),
]

for server_id, username_var, password_var in credentials:
    server = ET.SubElement(servers, tag("server"))
    ET.SubElement(server, tag("id")).text = server_id
    ET.SubElement(server, tag("username")).text = os.environ[username_var]
    ET.SubElement(server, tag("password")).text = os.environ[password_var]

tree.write(destination, encoding="UTF-8", xml_declaration=True)
PY

echo "========== Publish Maven Artifact =========="

mvn -B \
  -s "$SETTINGS_FILE" \
  -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
  deploy
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
'''
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Build, tests, and Nexus publication completed.'
        }

        failure {
            echo 'FAILED: Check the first failed stage in Console Output.'
        }

        always {
            echo 'CI pipeline finished.'
        }
    }
}
