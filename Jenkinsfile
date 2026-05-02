pipeline {
  agent any

  parameters {
    choice(name: 'DEPLOY_ENV', choices: ['dev', 'stage', 'master'], description: 'Target deployment environment')
  }

  environment {
    GHCR_OWNER = 'juancasanov'
    GHCR_REGISTRY = 'ghcr.io'
    IMAGE_TAG = 'latest'
    SERVICES = 'gateway auth identity form promotion dashboard'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build') {
      steps {
        sh './gradlew clean bootJar --parallel'
      }
    }

    stage('Test') {
      steps {
        sh './gradlew test -Dspring.profiles.active=test'
      }
    }

    stage('Docker Build') {
      steps {
        script {
          def serviceMap = [
            gateway: 'circleguard-gateway-service',
            auth: 'circleguard-auth-service',
            identity: 'circleguard-identity-service',
            form: 'circleguard-form-service',
            promotion: 'circleguard-promotion-service',
            dashboard: 'circleguard-dashboard-service'
          ]
          serviceMap.each { key, project ->
            def imageName = "${GHCR_REGISTRY}/${GHCR_OWNER}/${project}:${IMAGE_TAG}"
            sh "docker build -f services/${project}/Dockerfile -t ${imageName} ."
          }
        }
      }
    }

    stage('Docker Push') {
      steps {
        script {
          withCredentials([usernamePassword(credentialsId: 'ghcr-credentials', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
            sh "echo ${GHCR_TOKEN} | docker login ${GHCR_REGISTRY} -u ${GHCR_USER} --password-stdin"
            def projects = ['circleguard-gateway-service', 'circleguard-auth-service', 'circleguard-identity-service', 'circleguard-form-service', 'circleguard-promotion-service', 'circleguard-dashboard-service']
            for (project in projects) {
              def imageName = "${GHCR_REGISTRY}/${GHCR_OWNER}/${project}:${IMAGE_TAG}"
              sh "docker push ${imageName}"
              sh "docker rmi ${imageName} || true"
            }
            sh 'docker image prune -f'
            sh 'docker builder prune -f'
          }
        }
      }
    }

    stage('Deploy to Kubernetes') {
      steps {
        script {
          env.K8S_MANIFESTS = "k8s/${params.DEPLOY_ENV}"
          env.K8S_NAMESPACE = "circleguard-${params.DEPLOY_ENV}"
        }
        sh """
          kubectl create namespace "${K8S_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
          kubectl apply -f k8s/configmaps.yaml --namespace "${K8S_NAMESPACE}"
          kubectl apply -f k8s/secrets.yaml --namespace "${K8S_NAMESPACE}"
          kubectl apply -f "${K8S_MANIFESTS}" --namespace "${K8S_NAMESPACE}"
        """
      }
    }
  }

  post {
    success {
      echo 'Deployment complete.'
    }
    failure {
      echo 'Build or deployment failed. Review the logs.'
    }
  }
}
