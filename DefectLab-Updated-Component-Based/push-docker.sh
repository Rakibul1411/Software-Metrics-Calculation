#!/bin/bash
set -e

# ==============================================================================
# DefectLab Docker Build & Push Helper Script
# Usage:
#   ./push-docker.sh          -> Builds and pushes all services (frontend, backend, ml)
#   ./push-docker.sh frontend -> Builds and pushes ONLY frontend
#   ./push-docker.sh backend  -> Builds and pushes ONLY backend
#   ./push-docker.sh ml       -> Builds and pushes ONLY ml
# ==============================================================================

TARGET=${1:-all}
DOCKER_USER="rakibalnatiq"

echo "=========================================================="
echo "🚀 DefectLab Docker Image Publisher"
echo "Target: $TARGET"
echo "Docker Hub User: $DOCKER_USER"
echo "=========================================================="

PLATFORMS="linux/amd64,linux/arm64"

build_and_push_frontend() {
    echo ""
    echo "📦 [1/3] Building & Pushing Frontend (Angular) for $PLATFORMS..."
    docker buildx build --platform "$PLATFORMS" -f frontend-angular/Dockerfile -t ${DOCKER_USER}/defectlab-frontend:latest --push .
    echo "✅ Frontend published successfully for all platforms!"
}

build_and_push_backend() {
    echo ""
    echo "📦 [2/3] Building & Pushing Backend (Java Spring Boot) for $PLATFORMS..."
    docker buildx build --platform "$PLATFORMS" -f backend-java/Dockerfile -t ${DOCKER_USER}/defectlab-backend:latest --push .
    echo "✅ Backend published successfully for all platforms!"
}

build_and_push_ml() {
    echo ""
    echo "📦 [3/3] Building & Pushing ML Service (Python FastAPI) for $PLATFORMS..."
    docker buildx build --platform "$PLATFORMS" -f ml-service-python/Dockerfile -t ${DOCKER_USER}/defectlab-ml:latest --push .
    echo "✅ ML Service published successfully for all platforms!"
}

case "$TARGET" in
    frontend)
        build_and_push_frontend
        ;;
    backend)
        build_and_push_backend
        ;;
    ml)
        build_and_push_ml
        ;;
    all)
        build_and_push_frontend
        build_and_push_backend
        build_and_push_ml
        ;;
    *)
        echo "❌ Invalid service specified: '$TARGET'"
        echo "Valid options: all, frontend, backend, ml"
        exit 1
        ;;
esac

echo ""
echo "=========================================================="
echo "🎉 Done! New Docker images are live on Docker Hub."
echo "Users will automatically receive the updates next time they open DefectLab."
echo "=========================================================="
