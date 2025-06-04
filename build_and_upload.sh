#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration ---
AWS_ACCOUNT_ID="471112541871"
AWS_REGION="ap-south-1"
ECR_REPOSITORY_NAME="ridesense-prod-graphhopper-repository"
LOCAL_IMAGE_NAME="graphhopper" # The name given to the image during docker build

# --- Argument Parsing ---
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <ecr_tag> <pbf_file_path>"
    echo "Example: $0 v1.0.0 /path/to/your/map_data.pbf"
    exit 1
fi

ECR_TAG="$1"
PBF_FILE_PATH="$2"

if [ -z "$ECR_TAG" ]; then
    echo "Error: ECR tag cannot be empty."
    exit 1
fi

if [ -z "$PBF_FILE_PATH" ]; then
    echo "Error: PBF file path cannot be empty."
    exit 1
fi

if [ ! -f "$PBF_FILE_PATH" ]; then
    echo "Error: PBF file not found at $PBF_FILE_PATH"
    exit 1
fi

ECR_IMAGE_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY_NAME}:${ECR_TAG}"
LOCAL_IMAGE_WITH_LATEST_TAG="${LOCAL_IMAGE_NAME}:latest" # docker build -t name . creates name:latest

# --- Build Docker Image ---
echo "Building Docker image..."
# Ensure PBF_PATH is relative to the build context (.) if it's not an absolute path
# For simplicity here, we assume PBF_FILE_PATH might be absolute or relative to where the script is run,
# and docker build needs it relative to the Dockerfile context (which is '.')
# If PBF_FILE_PATH is not already in the docker context, this build command will fail.
# The user must ensure PBF_FILE_PATH is accessible by the docker daemon, typically by
# placing it within the build context (e.g. the current directory or a subdirectory).
docker build --build-arg PBF_PATH="$PBF_FILE_PATH" -t "$LOCAL_IMAGE_NAME" .
echo "Docker image built successfully: $LOCAL_IMAGE_WITH_LATEST_TAG"

# --- Log in to AWS ECR ---
echo "Logging in to AWS ECR..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
echo "Successfully logged in to ECR."

# --- Tag Docker Image for ECR ---
echo "Tagging image $LOCAL_IMAGE_WITH_LATEST_TAG as $ECR_IMAGE_URI..."
docker tag "$LOCAL_IMAGE_WITH_LATEST_TAG" "$ECR_IMAGE_URI"
echo "Image tagged successfully."

# --- Push Docker Image to ECR ---
echo "Pushing image $ECR_IMAGE_URI to ECR..."
docker push "$ECR_IMAGE_URI"
echo "Image pushed successfully to ECR: $ECR_IMAGE_URI"

echo "Build and upload complete."
