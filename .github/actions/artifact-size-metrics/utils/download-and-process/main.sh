#!/bin/bash

# Bash script to download, and compare artifact size metrics

source "$(dirname "$0")/compare.sh"
source "$(dirname "$0")/../s3.sh"
source "$(dirname "$0")/../constants.sh"
source "$(dirname "$0")/../setup.sh"

setup

if [ "$DOWNLOAD" == "true" ]; then
  # Get metrics calculated in codebuild - otherwise metrics will already be here
  downloadFromMetricsBucket [TEMP]"$GITHUB_REPOSITORY"-pull-request-"$IDENTIFIER".csv "$metrics_file" # see: constants.sh
fi

# Metrics from the latest release are never calculated here so we need to download them
downloadFromMetricsBucket "$GITHUB_REPOSITORY"-latest.csv "$latest_release_metrics_file" # see: constants.sh

# Compare metrics
export LARGE_DIFF=$(compareMetrics "$metrics_file" "$latest_release_metrics_file" "$metrics_comparison_file") # see: constants.sh

if [ "$LARGE_DIFF" == "true" ]; then
  echo "Large diff found!"
fi
