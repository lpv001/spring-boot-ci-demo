
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(
            numToKeepStr: '20',
            artifactNumToKeepStr: '10'
        ))
    }

    environment {
        NEXUS_URL = 'http://nexus.nexus.svc.cluster.local:8081'
        NEXUS_GROUP_REPO = 'maven-public'
        NEXUS_SNAPSHOT_REPO = 'maven-snapshots'

        // Isolate this build from any pre-existing Maven cache.
        MAVEN_LOCAL_REPO = "${WORKSPACE}/.m2-ci"
    }

    stages {
        stage('Check Build Environment') {
            steps {
                sh '''
                    set -eu

                    echo "Java:"
                    java -version

                    echo "Maven:"
                    mvn -version

                    echo "Checking Nexus Maven group..."
                    curl --fail --silent --show-error \
                      "$NEXUS_URL/repository/$NEXUS_GROUP_REPO/" \
                      -o /dev/null
                '''
            }
        }

        stage('Checkout') {
            steps {
                checkout scm

                sh '''
                    echo "Git commit:"
                    git rev-parse HEAD

                    echo "Project files:"
                    ls -la
                    test -f pom.xml
                '''
            }
        }

        stage('Verify Maven Uses Nexus') {
            steps {
                sh '''
                    set -eu

                    test -f "$WORKSPACE/ci-settings.xml"

                    echo "Maven mirror configuration:"
                    python3 - <<'PY'
                    import xml.etree.ElementTree as ET
                    import os

                    path = os.path.join(
                        os.environ["WORKSPACE"], "ci-settings.xml"
                    )
                    root = ET.parse(path).getroot()
                    ns = {"m": root.tag.split("}")[0].strip("{")}

                    mirrors = root.findall("m:mirrors/m:mirror", ns)
                    found = any(
                        m.findtext("m:mirrorOf", default="", namespaces=ns) == "*"
                        and "maven-public" in m.findtext(
                            "m:url", default="", namespaces=ns
                        )
                        for m in mirrors
                    )

                    if not found:
                        raise SystemExit(
                            "ERROR: ci-settings.xml must mirror * "
                            "to the Nexus maven-public group"
                        )

                    print("OK: all Maven repository IDs are mirrored to Nexus")
                    PY
                '''
            }
        }

        stage('Clean and Test') {
            steps {
                sh '''
                    set -eu

                    mvn -B \
                      -s "$WORKSPACE/ci-settings.xml" \
                      -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
                      clean verify
                '''
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

        stage('Publish JAR to Nexus') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-publisher',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
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
                        ns = {"m": root.tag.split("}")[0].strip("{")}
                        tag = lambda name: "{%s}%s" % (
                            root.tag.split("}")[0].strip("{"), name
                        )

                        servers = root.find("m:servers", ns)
                        if servers is None:
                            servers = ET.SubElement(root, tag("servers"))

                        server = ET.SubElement(servers, tag("server"))
                        ET.SubElement(server, tag("id")).text = "nexus-snapshots"
                        ET.SubElement(server, tag("username")).text = os.environ["NEXUS_USERNAME"]
                        ET.SubElement(server, tag("password")).text = os.environ["NEXUS_PASSWORD"]

                        tree.write(
                            destination,
                            encoding="UTF-8",
                            xml_declaration=True
                        )
                        PY

                        mvn -B \
                          -s "$SETTINGS_FILE" \
                          -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
                          deploy
                    '''
                }
            }
        }

        stage('Verify Build Artifact') {
            steps {
                sh '''
                    set -eu

                    echo "Generated JARs:"
                    find . -path '*/target/*.jar' \
                      -type f ! -name '*-sources.jar' \
                      ! -name '*-javadoc.jar' -print

                    echo "Build commit:"
                    git rev-parse HEAD
                '''
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Maven build and Nexus publication completed.'
        }
        failure {
            echo 'FAILED: Inspect the first failed stage in the Jenkins console log.'
        }
        always {
            echo 'CI pipeline finished.'
        }
    }
}
