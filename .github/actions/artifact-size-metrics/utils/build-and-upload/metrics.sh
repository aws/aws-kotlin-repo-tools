# Gets artifact size metrics from staging dir
getArtifactSizes() {
  # Create output file
  output="build/reports/metrics/artifact-size-metrics.csv"
  mkdir -p "$(dirname "$output")"
  touch "$output"

  # Write CSV header
  echo "Jar File,Size (Bytes)" > "$output"

  # Find all JARs (exclude sources and javadoc)
  # TODO: Calculate KN artifacts sizes
  find build/m2 -type f -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | while read -r jar; do
      size=$(stat -c%s "$jar")
      # strip "-<version>(-timestamp?)" before ".jar"
      artifact=$(echo "$jar" | sed -E 's/-[0-9]+(\.[0-9]+)*(-[0-9]{8}\.[0-9]{6}-[0-9]+)?\.jar$/.jar/')
      echo "\"$artifact\",$size" >> "$output"
  done
}
