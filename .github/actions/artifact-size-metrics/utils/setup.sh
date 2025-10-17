# Exit if non zero exit code or if env var is missing, and enable command tracing
setup() {
  set -u
  set -e
  set -x
}
