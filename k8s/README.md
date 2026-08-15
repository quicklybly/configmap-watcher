# Kubernetes test environment

A local [kind](https://kind.sigs.k8s.io) cluster running `test-application`.

## Prerequisites

```bash
brew install kind kubernetes-cli
open -a Docker                     # the daemon has to be running
```

`up.sh` checks all three and tells you which one is missing.

## Quick start

```bash
k8s/up.sh
```

```bash
kubectl --context kind-configmap-watcher port-forward svc/test-application 8080:8080
kubectl --context kind-configmap-watcher logs -f deployment/test-application
```

and change the config:

```bash
curl -s localhost:8080/message      # initial
k8s/set-message.sh hello
curl -s localhost:8080/message      # hello, within ~10s
```

Tear everything down with `k8s/down.sh`, which deletes the cluster.
