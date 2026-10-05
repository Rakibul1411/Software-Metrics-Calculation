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

build_and_push_frontend() {
    echo ""
    echo "📦 [1/3] Building & Pushing Frontend (Angular)..."
    docker compose build frontend
    docker tag defectlab-updated-component-based-frontend:latest ${DOCKER_USER}/defectlab-frontend:latest
    docker push ${DOCKER_USER}/defectlab-frontend:latest
    echo "✅ Frontend published successfully!"
}

build_and_push_backend() {
    echo ""
    echo "📦 [2/3] Building & Pushing Backend (Java Spring Boot)..."
    docker compose build backend
    docker tag defectlab-updated-component-based-backend:latest ${DOCKER_USER}/defectlab-backend:latest
    docker push ${DOCKER_USER}/defectlab-backend:latest
    echo "✅ Backend published successfully!"
}

build_and_push_ml() {
    echo ""
    echo "📦 [3/3] Building & Pushing ML Service (Python FastAPI)..."
    docker compose build ml
    docker tag defectlab-updated-component-based-ml:latest ${DOCKER_USER}/defectlab-ml:latest
    docker push ${DOCKER_USER}/defectlab-ml:latest
    echo "✅ ML Service published successfully!"
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
