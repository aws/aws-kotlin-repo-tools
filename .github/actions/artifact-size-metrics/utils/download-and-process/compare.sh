# Compares artifact size metrics to ones from the latest available release,
# stores comparison as a markdown table,
# and returns "true" if a large diff was found (over 5%)
compareMetrics() {
    local metrics_file="$1"
    local latest_release_metrics_file="$2"
    local metrics_comparison_file="$3"

    # Title and table headers
    {
      echo "Affected Artifacts"
      echo "="
      echo "| Artifact | Pull Request (bytes) | Latest Release (bytes) | Delta (bytes) | Delta (percentage) |"
      echo "|----------|----------------------|------------------------|---------------|--------------------|"
    } > "$metrics_comparison_file"

    large_diff=false

    # Read CSV
    while IFS=',' read -r artifact size; do
      # Skip header
      [ "$artifact" = "Artifact" ] && continue

      # Trim spaces
      artifact=$(echo "$artifact" | xargs)
      size=$(echo "$size" | xargs)

      # Find corresponding artifact size in release file or skip
      latest_release_size=$(awk -F',' -v art="$artifact" 'NR>1 && $1==art {gsub(/ /,"",$2); print $2}' "$latest_release_metrics_file")
      [ -z "$latest_release_size" ] && continue

      # Find delta
      delta=$((size - latest_release_size))
      abs_delta=${delta#-}
      percent=$((100 * abs_delta / latest_release_size))

      # Add to file
      echo "| $artifact | $size | $latest_release_size | $delta | ${percent}% |" >> "$metrics_comparison_file"

      # Check for large diff
      if [ "$percent" -gt 5 ]; then
          large_diff=true
      fi
    done < "$metrics_file"

    # Print results for debugging
    cat "$metrics_comparison_file"

    $large_diff && echo "true"
}
