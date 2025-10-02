# Owned by: aws-kotlin-sdk+ci
S3_ARTIFACT_SIZE_METRICS_BUCKET="artifact-size-metrics-2" # TODO: Remove "-2" when done testing

# Uploads metrics to the metrics bucket under the specified file name
uploadToS3() {
  aws s3 cp "$1" s3://"$S3_ARTIFACT_SIZE_METRICS_BUCKET"/"$2"
}

# Downloads metrics from the metrics bucket to the specified local file
downloadFromS3() {
  aws s3 cp s3://"$S3_ARTIFACT_SIZE_METRICS_BUCKET"/"$1" "$2"
}