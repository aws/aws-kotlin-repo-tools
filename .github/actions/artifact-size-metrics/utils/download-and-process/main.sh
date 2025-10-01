#!/bin/bash

source ./compare.sh
source ../s3.sh
source ../setup.sh
setup

# Get metrics from pull request and from latest release
if [ "$DOWNLOAD" == "true" ]; then
  downloadFromS3 [TEMP]"$GITHUB_REPOSITORY"-pull-request-"$IDENTIFIER".csv ./current.csv
fi
downloadFromS3 "$GITHUB_REPOSITORY"-latest.csv ./latest.csv

# Compare metrics and save if large diff was found
export LARGE_DIFF=$(compareMetrics current.csv latest.csv comparisson.md)
