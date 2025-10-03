# Gets artifact size metrics from staging dir
calculateArtifactSizes() {
  # Artifact staging dir
  input_dir="build/m2"

  # Create output_file
  output_file="$1"
  mkdir -p "$(dirname "$output_file")"
  touch "$output_file"

  # Write CSV header
  echo "Artifact, Size (Bytes)" > "$output_file"

  # Find all JARs (exclude sources and javadoc)
  # TODO: Calculate KN artifacts sizes
  find "$input_dir" -type f -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | while read -r jar; do
      size=$(stat -c%s "$jar")

      # remove dir path, version, optional timestamp, and .jar
      artifact=$(basename "$jar")
      artifact=$(echo "$artifact" | sed -E 's/-[0-9].*\.jar$//')

      # Add artifact size to CSV
      echo "$artifact, $size" >> "$output_file"
  done

  # Print results for debugging
  cat "$output_file"
}
