# Upload the artifact size metrics to cloudwatch
uploadToCloudwatch() {
  metrics_file="$1"
  metrics=()

  # Read CSV
  while IFS=',' read -r artifactName artifactSize; do
      # Skip header
      [[ "$artifactName" == "Artifact" ]] && continue

      # Trim spaces
      artifactName=$(echo "$artifactName" | xargs)
      artifactSize=$(echo "$artifactSize" | xargs)

      # Build metric JSON
      metrics+=$(jq -n \
          --arg name "$GITHUB_REPOSITORY-$artifactName" \
          --arg value "$artifactSize" \
          --arg project "$GITHUB_REPOSITORY" \
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
  done < "$metrics_file"

  namespace="Artifact Size Metrics"
  chunk_size=100

  # Send metrics in chunks
  for ((i=0; i<${#metrics[@]}; i+=chunk_size)); do
      chunk=("${metrics[@]:i:chunk_size}")
      aws cloudwatch put-metric-data \
          --namespace "$namespace" \
          --metric-data "$(printf '%s\n' "${chunk[@]}" | jq -s '.')"
  done
}
