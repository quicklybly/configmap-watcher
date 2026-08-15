#!/bin/sh
# Shared settings and helpers. Sourced by the other scripts, not run directly.

CLUSTER_NAME=configmap-watcher
IMAGE=configmap-watcher-test-app:local
DEPLOYMENT=test-application
CONFIG_MAP=test-application-config
KUBE_CONTEXT="kind-$CLUSTER_NAME"

K8S_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(dirname -- "$K8S_DIR")

die() {
    echo "error: $*" >&2
    exit 1
}

require_tools() {
    command -v docker >/dev/null 2>&1 || die "docker is not installed"
    command -v kind >/dev/null 2>&1 || die "kind is not installed - run: brew install kind"
    command -v kubectl >/dev/null 2>&1 || die "kubectl is not installed - run: brew install kubernetes-cli"
    docker info >/dev/null 2>&1 || die "the docker daemon is not running - run: open -a Docker"
}

require_cluster() {
    kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME" ||
        die "cluster '$CLUSTER_NAME' does not exist - run: $K8S_DIR/up.sh"
}

# Always target this cluster explicitly, so nothing here can touch whatever context the user happens
# to have selected.
kube() {
    kubectl --context "$KUBE_CONTEXT" "$@"
}

build_and_load_image() {
    echo "==> building $IMAGE (repository root as build context)"
    docker build -f "$REPO_ROOT/test-application/Dockerfile" -t "$IMAGE" "$REPO_ROOT"

    echo "==> loading $IMAGE into cluster '$CLUSTER_NAME'"
    kind load docker-image "$IMAGE" --name "$CLUSTER_NAME"
}
