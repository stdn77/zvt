#!/bin/bash
# ===== ZVIT Backend Deploy Script =====
# Usage: ./deploy.sh [version] [environment]
# Example: ./deploy.sh v1.2.0 prod

set -e

# Configuration
VERSION=${1:-latest}
ENVIRONMENT=${2:-prod}
AWS_REGION=${AWS_REGION:-eu-central-1}
ECR_REPOSITORY="zvit-backend"
ECS_CLUSTER="zvit-cluster"
ECS_SERVICE="zvit-backend-service"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check requirements
check_requirements() {
    log_info "Checking requirements..."

    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi

    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI is not installed"
        exit 1
    fi

    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        log_error "AWS credentials not configured. Run 'aws configure'"
        exit 1
    fi

    log_info "All requirements met"
}

# Get AWS account ID
get_account_id() {
    aws sts get-caller-identity --query Account --output text
}

# Get ECR URI
get_ecr_uri() {
    local account_id=$(get_account_id)
    echo "${account_id}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}"
}

# Login to ECR
ecr_login() {
    log_info "Logging in to ECR..."
    local ecr_uri=$(get_ecr_uri)
    aws ecr get-login-password --region ${AWS_REGION} | \
        docker login --username AWS --password-stdin ${ecr_uri%/*}
}

# Build Docker image
build_image() {
    log_info "Building Docker image..."
    docker build -t ${ECR_REPOSITORY}:${VERSION} .
    docker tag ${ECR_REPOSITORY}:${VERSION} ${ECR_REPOSITORY}:latest
}

# Push to ECR
push_to_ecr() {
    log_info "Pushing image to ECR..."
    local ecr_uri=$(get_ecr_uri)

    docker tag ${ECR_REPOSITORY}:${VERSION} ${ecr_uri}:${VERSION}
    docker tag ${ECR_REPOSITORY}:${VERSION} ${ecr_uri}:latest

    docker push ${ecr_uri}:${VERSION}
    docker push ${ecr_uri}:latest

    log_info "Image pushed: ${ecr_uri}:${VERSION}"
}

# Update ECS service
update_ecs_service() {
    log_info "Updating ECS service..."

    aws ecs update-service \
        --cluster ${ECS_CLUSTER} \
        --service ${ECS_SERVICE} \
        --force-new-deployment \
        --region ${AWS_REGION}

    log_info "ECS service update initiated"
}

# Wait for deployment
wait_for_deployment() {
    log_info "Waiting for deployment to complete..."

    aws ecs wait services-stable \
        --cluster ${ECS_CLUSTER} \
        --services ${ECS_SERVICE} \
        --region ${AWS_REGION}

    log_info "Deployment completed successfully!"
}

# Main deploy function
deploy() {
    echo "========================================"
    echo "  ZVIT Backend Deployment"
    echo "  Version: ${VERSION}"
    echo "  Environment: ${ENVIRONMENT}"
    echo "  Region: ${AWS_REGION}"
    echo "========================================"
    echo ""

    check_requirements
    ecr_login
    build_image
    push_to_ecr
    update_ecs_service

    echo ""
    log_info "Deployment initiated!"
    log_info "Check AWS Console for deployment status."
    echo ""

    read -p "Wait for deployment to complete? (y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        wait_for_deployment
    fi
}

# Build only (for local testing)
build_only() {
    log_info "Building Docker image locally..."
    build_image
    log_info "Build complete. Run 'docker-compose up' to test locally."
}

# Show help
show_help() {
    echo "ZVIT Backend Deploy Script"
    echo ""
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  deploy [version]    Build and deploy to AWS (default command)"
    echo "  build               Build Docker image locally"
    echo "  push [version]      Push existing image to ECR"
    echo "  help                Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 v1.2.0           Deploy version 1.2.0"
    echo "  $0 build            Build locally for testing"
    echo "  $0 push v1.2.0      Push v1.2.0 to ECR"
}

# Parse command
case "${1}" in
    build)
        build_only
        ;;
    push)
        VERSION=${2:-latest}
        check_requirements
        ecr_login
        push_to_ecr
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        deploy
        ;;
esac
