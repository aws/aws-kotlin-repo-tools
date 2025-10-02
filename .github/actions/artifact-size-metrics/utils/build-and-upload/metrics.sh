# Gets artifact size metrics from staging dir
getArtifactSizes() {
  # Staging dir
  input="build/m2"

  # Create output file
  output="build/reports/metrics/artifact-size-metrics.csv"
  mkdir -p "$(dirname "$output")"
  touch "$output"

  # Write CSV header
  echo "Artifact, Size (Bytes)" > "$output"

  # Find all JARs (exclude sources and javadoc)
  # TODO: Calculate KN artifacts sizes
  find "$input" -type f -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | while read -r jar; do
      size=$(stat -c%s "$jar")
#      size=$(stat -f%z "$jar")  # TODO: Remove when done testing

      # remove dir path, version, optional timestamp, and .jar
      artifact=$(basename "$jar")
      artifact=$(echo "$artifact" | sed -E 's/-[0-9].*\.jar$//')

      # Add JAR to CSV
      echo "$artifact, $size" >> "$output"
  done

  # Print results for debugging
  cat "$output"
}
