
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        NEXUS_URL =
            'http://nexus.nexus.svc.cluster.local:8081/repository/maven-snapshots/'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm

                sh '''
                    echo "Commit: $(git rev-parse --short HEAD)"
                    echo "Branch: ${BRANCH_NAME:-manual}"
                    test -f pom.xml
                    test -f mvnw
                '''
            }
        }

        stage('Check Java') {
            steps {
                sh '''
                    java -version
                    chmod +x mvnw
                    ./mvnw -version
                '''
            }
        }

        stage('Build and Test') {
            steps {
                sh './mvnw -B clean verify'
            }
        }

        stage('Publish to Nexus') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-publisher',
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x
                        mkdir -p "$HOME/.m2"

                        cat > "$HOME/.m2/settings.xml" <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <servers>
    <server>
      <id>nexus-snapshots</id>
      <username>${env.NEXUS_USERNAME}</username>
      <password>${env.NEXUS_PASSWORD}</password>
    </server>
  </servers>
</settings>
EOF

                        ./mvnw -B deploy -DskipTests
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'CI succeeded: tested and published to Nexus.'
        }
        failure {
            echo 'CI failed. Review the stage logs above.'
        }
        always {
            sh 'rm -f "$HOME/.m2/settings.xml"' 
        }
    }
}
