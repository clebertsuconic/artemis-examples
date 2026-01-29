# High Availability with Mirroring and Distributed Locks

A modification of ha-with-mirroring providing an extra component to connect to etcd.

## Overview

A custom lock-manager is provided to add etcd support and the lock coordinator is then configured to connect to etcd.

## Compile the lock-manager plugin

Before running this example you need to compile the plugin.
```shell
- cd examples/features/sub-modules/distributed-lock-example-etcd
- mvn install
```

## start etcd

You need to provide etcd running. An easy way to do this is with either docker or podman:

There is a script here that you can use to start-etcd.sh
```shell
cd ./scripts
./start-etcd.sh
```

You can now run the test:

```shell
mvn clean verify
```