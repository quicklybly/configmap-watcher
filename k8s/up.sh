#!/bin/sh
# Creates the cluster if needed, builds and loads the image, and deploys. Safe to re-run.
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

require_tools

if kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
    echo "==> cluster '$CLUSTER_NAME' already exists, reusing it"
else
    echo "==> creating cluster '$CLUSTER_NAME'"
    kind create cluster --config "$K8S_DIR/kind-cluster.yaml"
fi

# Applying an unchanged Deployment is a no-op, so an existing one has to be rolled explicitly to pick
# up a rebuilt image that reuses the same tag.
if kube get deployment "$DEPLOYMENT" >/dev/null 2>&1; then
    redeploy=yes
else
    redeploy=no
fi

build_and_load_image

echo "==> applying manifests"
kube apply -f "$K8S_DIR/manifests"

if [ "$redeploy" = yes ]; then
    echo "==> rolling the deployment onto the rebuilt image"
    kube rollout restart "deployment/$DEPLOYMENT"
fi

echo "==> waiting for the rollout"
kube rollout status "deployment/$DEPLOYMENT" --timeout=180s

cat <<EOF

Ready. In separate terminals:

  kubectl --context $KUBE_CONTEXT port-forward svc/$DEPLOYMENT 8080:8080
  kubectl --context $KUBE_CONTEXT logs -f deployment/$DEPLOYMENT

Then change the config and watch the pod pick it up without restarting:

  curl -s localhost:8080/message          # initial
  $K8S_DIR/set-message.sh hello
  curl -s localhost:8080/message          # hello, within ~10s

Tear down with $K8S_DIR/down.sh
EOF
