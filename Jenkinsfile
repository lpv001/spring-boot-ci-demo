
stage('Clean and Test') {
    steps {
        withCredentials([
            usernamePassword(
                credentialsId: 'nexus-reader',
                usernameVariable: 'NEXUS_USER',
                passwordVariable: 'NEXUS_PASSWORD'
            )
        ]) {
            sh '''
set -eu
set +x
umask 077

SETTINGS_FILE="$(mktemp)"
trap 'rm -f "$SETTINGS_FILE"' EXIT

cat ci-settings.xml > "$SETTINGS_FILE"

# Insert credentials into the mirror's server entry.
sed '/<\/settings>/i\\
    <servers>\\
        <server>\\
            <id>nexus-public</id>\\
            <username>'"$NEXUS_USER"'</username>\\
            <password>'"$NEXUS_PASSWORD"'</password>\\
        </server>\\
    </servers>' ci-settings.xml > "$SETTINGS_FILE"

mvn -B -s "$SETTINGS_FILE" \
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
                usernameVariable: 'NEXUS_USER',
                passwordVariable: 'NEXUS_PASSWORD'
            ),
            usernamePassword(
                credentialsId: 'nexus-publisher',
                usernameVariable: 'PUBLISH_USER',
                passwordVariable: 'PUBLISH_PASSWORD'
            )
        ]) {
            sh '''
set -eu
set +x
umask 077

SETTINGS_FILE="$(mktemp)"
trap 'rm -f "$SETTINGS_FILE"' EXIT

sed '/<\/settings>/i\\
    <servers>\\
        <server>\\
            <id>nexus-public</id>\\
            <username>'"$NEXUS_USER"'</username>\\
            <password>'"$NEXUS_PASSWORD"'</password>\\
        </server>\\
        <server>\\
            <id>nexus-snapshots</id>\\
            <username>'"$PUBLISH_USER"'</username>\\
            <password>'"$PUBLISH_PASSWORD"'</password>\\
        </server>\\
    </servers>' ci-settings.xml > "$SETTINGS_FILE"

mvn -B -s "$SETTINGS_FILE" \
    -Dmaven.repo.local="$MAVEN_LOCAL_REPO" \
    deploy
'''
        }
    }
}
