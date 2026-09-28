#!/bin/bash

source config.sh

# Step 1: launch a vm instance. stores id on instance.id
$DIR/launch-vm.sh

# Step 2: install software in the VM instance.
$DIR/install-loadbalancer.sh
