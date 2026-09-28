#!/bin/bash

source config.sh

# Step 1: launch a vm instance. stores id on instance.id
$DIR/launch-vm.sh

# Step 2: install software in the VM instance.
$DIR/install-vm.sh

while read -r id ip; do
    echo "ID: $id, IP: $ip"
    # Step 4: create VM image (AIM).
    name="CNV-Image"
    aws ec2 create-image --instance-id $id --name "$name" | jq -r .ImageId > image.id
    echo "New VM image with id $id."

    # Step 5: Wait for image to become available.
    echo "Waiting for image to be ready... (this can take a couple of minutes)"
    aws ec2 wait image-available --filters Name=name,Values=$name
    echo "Waiting for image to be ready... done! \o/"

    # Step 6: terminate the vm instance.
    aws ec2 terminate-instances --instance-ids $id
done < instance.id
rm -f instance.id
rm -f instances_to_install.id

