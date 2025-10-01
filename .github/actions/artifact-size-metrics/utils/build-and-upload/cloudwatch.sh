# Upload the artifact size metrics to cloudwatch
uploadToCloudwatch() {
  set -o pipefail

  METRICS_FILE="metrics.csv"      # path to your CSV file
  PROJECT_REPO_NAME=$1
  NAMESPACE="Artifact Size Metrics"

  # Read CSV, skipping header
  tail -n +2 "$METRICS_FILE" | while IFS=',' read -r artifactName artifactSize; do
      artifactName=$(echo "$artifactName" | xargs) # trim spaces
      artifactSize=$(echo "$artifactSize" | xargs)

      # Build JSON for CloudWatch
      metric_json=$(jq -n \
          --arg name "$PROJECT_REPO_NAME-$artifactName" \
          --arg value "$artifactSize" \
          --arg project "$PROJECT_REPO_NAME" \
          '{
              MetricName: $name,
              Timestamp: (now | todate),
              Unit: "Bytes",
              Value: ($value | tonumber),
              Dimensions: [
                  { Name: "Project", Value: $project }
              ]
          }'
      )

      METRICS+=("$metric_json")
  done

  # Send metrics in chunks of 1000
  chunk_size=1000
  for ((i=0; i<${#METRICS[@]}; i+=chunk_size)); do
      chunk=("${METRICS[@]:i:chunk_size}")
      aws cloudwatch put-metric-data \
          --namespace "$NAMESPACE" \
          --metric-data "$(printf '%s\n' "${chunk[@]}" | jq -s '.')"
  done
}
