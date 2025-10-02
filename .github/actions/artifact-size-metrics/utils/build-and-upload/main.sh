#!/bin/bash

# Bash script to build, calculate, and upload artifact size metrics

source "$(dirname "$0")/calculate_metrics.sh"
source "$(dirname "$0")/cloudwatch.sh"
source "$(dirname "$0")/../s3.sh"
source "$(dirname "$0")/../constants.sh"
source "$(dirname "$0")/../setup.sh"

setup

# Build
if [ "$GITHUB_REPOSITORY" = "aws-sdk-kotlin" ]; then
    # FIXME: Enable K/N builds
    ./gradlew build -Paws.kotlin.native=false build --parallel --max-workers 16
else
    ./gradlew build
fi

# Move artifacts that'll be published to staging dir (build/m2)
./gradlew publish --parallel --max-workers 16

# Calculate size for artifacts in staging dir (build/m2) and save them to metrics_file
calculateArtifactSizes "$metrics_file" # see: constants.sh

# Upload metrics to S3/cloudwatch if required
if [ "$UPLOAD" == "true" ]; then
  if [ "$RELEASE_METRICS" == "true" ]; then
    # For record-keeping
    uploadToMetricsBucket "$metrics_file" "$GITHUB_REPOSITORY"-v"$IDENTIFIER".csv
    uploadToMetricsBucket "$metrics_file" "$GITHUB_REPOSITORY"-latest.csv

    # For display in our OPS dashboard
    uploadToCloudwatch "$metrics_file" "$GITHUB_REPOSITORY"
  else
    # For downstream consumption in pull requests
    uploadToMetricsBucket "$metrics_file" [TEMP]"$GITHUB_REPOSITORY"-pull-request-"$IDENTIFIER".csv
  fi
fi
