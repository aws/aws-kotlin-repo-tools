#!/bin/bash

source ./metrics.sh
source ./cloudwatch.sh
source ../s3.sh
source ../setup.sh

setup

# Build
if [ "$GITHUB_REPOSITORY" = "aws-sdk-kotlin" ]; then
    # FIXME: Enable K/N builds
    ./gradlew build -Paws.kotlin.native=false build --parallel --max-workers 16
else
    ./gradlew build
fi

# Move artifacts that will be published to staging dir (build/m2)
./gradlew publish

# Calculate size for artifacts in staging dir
getArtifactSizes

# Upload size metrics
if [ "$UPLOAD" == "true" ]; then
  if [ "$RELEASE_METRICS" == "true" ]; then
    # For record-keeping
    uploadToS3 "$GITHUB_REPOSITORY"-v"$IDENTIFIER".csv
    uploadToS3 "$GITHUB_REPOSITORY"-latest.csv

    # For display in our OPS dashboard
    uploadToCloudwatch "$GITHUB_REPOSITORY"
  else
    # For downstream consumption in pull requests
    uploadToS3 [TEMP]"$GITHUB_REPOSITORY"-pull-request-"$IDENTIFIER".csv
  fi
fi
