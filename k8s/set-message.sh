#!/bin/sh
# Changes the watched value in the ConfigMap. This is the event the whole environment exists to test.
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

[ $# -eq 1 ] || die "usage: $(basename -- "$0") <message>"
message=$1

# The patch below embeds the message in JSON, and this is a test helper rather than a sanitiser.
case $message in
    *[\"\\]*) die "message must not contain quotes or backslashes" ;;
esac

require_tools
require_cluster

echo "==> setting config-map.message to '$message'"
kube patch configmap "$CONFIG_MAP" --type merge \
    -p "$(printf '{"data":{"application.yaml":"config-map:\\n  message: %s\\n"}}' "$message")"

cat <<EOF

The ConfigMap object is updated now, but the file inside the pod is not. The kubelet rewrites the
mount on its sync loop - kind-cluster.yaml sets syncFrequency to 10s - and only then does the watcher
see it. Watch it land with:

  kubectl --context $KUBE_CONTEXT logs -f deployment/$DEPLOYMENT
EOF
