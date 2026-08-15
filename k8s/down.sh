#!/bin/sh
# Deletes the whole cluster, which is also how everything deployed into it is cleaned up.
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

command -v kind >/dev/null 2>&1 || die "kind is not installed"

if kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
    echo "==> deleting cluster '$CLUSTER_NAME'"
    kind delete cluster --name "$CLUSTER_NAME"
else
    echo "cluster '$CLUSTER_NAME' does not exist, nothing to do"
fi
