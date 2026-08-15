#!/bin/sh
# Rebuilds the image and rolls the deployment onto it, for iterating on application code without
# recreating the cluster.
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

require_tools
require_cluster

build_and_load_image

echo "==> rolling the deployment"
kube rollout restart "deployment/$DEPLOYMENT"
kube rollout status "deployment/$DEPLOYMENT" --timeout=180s
