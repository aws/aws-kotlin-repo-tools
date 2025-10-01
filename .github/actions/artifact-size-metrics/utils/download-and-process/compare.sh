# Compares two metrics files, saves results into a file, returns "true" if large diff was found
compareMetrics() {
    local pr_file="$1"
    local release_file="$2"
    local output_file="$3"

    # Create header for the Markdown table
    echo "| Artifact | Pull Request (bytes) | Latest Release (bytes) | Delta (bytes) | Delta (percentage) |" > "$output_file"
    echo "|----------|--------------------|----------------------|---------------|------------------|" >> "$output_file"

    local flag=false

    # Read PR file line by line (skip header)
    tail -n +2 "$pr_file" | while IFS=',' read -r artifact pr_size; do
        # Trim whitespace
        artifact=$(echo "$artifact" | xargs)
        pr_size=$(echo "$pr_size" | xargs)

        # Find corresponding artifact in release file
        release_size=$(awk -F',' -v art="$artifact" 'NR>1 && $1 ~ art {gsub(/ /,"",$2); print $2}' "$release_file")

        # Skip if artifact not found in release file
        [ -z "$release_size" ] && continue

        # Compute delta and percentage
        delta=$((pr_size - release_size))
        abs_delta=${delta#-}
        percent=$((100 * abs_delta / release_size))

        # Add row to Markdown table
        echo "| $artifact | $pr_size | $release_size | $delta | $percent% |" >> "$output_file"

        # Check if delta > 5%
        if [ "$percent" -gt 5 ]; then
            flag=true
        fi
    done

    $flag && echo "true"
}
