#! /bin/bash

BUILDKIT_PROGRESS=plain DOCKER_BUILDKIT=1 docker buildx build \
  -t graphhopper-service --file alltrails/Dockerfile . 