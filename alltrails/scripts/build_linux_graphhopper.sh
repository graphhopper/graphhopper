#! /bin/bash

echo "Building graphhopper-service:graphhopper-service-test"
BUILDKIT_PROGRESS=plain DOCKER_BUILDKIT=1 docker buildx build \
  -t "graphhopper-service:graphhopper-service-test" \
  --platform linux/amd64 --file alltrails/Dockerfile . 

echo "Tagging graphhopper-service:graphhopper-service-test"
docker tag graphhopper-service:graphhopper-service-test 873326996015.dkr.ecr.us-west-2.amazonaws.com/graphhopper-service:graphhopper-service-test

echo "Pushing graphhopper-service:graphhopper-service-test"
docker push 873326996015.dkr.ecr.us-west-2.amazonaws.com/graphhopper-service:graphhopper-service-test