#!/bin/bash

source config.sh

IMAGE_IDS=$(aws ec2 describe-images --owners self --query "Images[].ImageId" --output text)

SNAP_IDS=$(aws ec2 describe-snapshots --owner-ids self --query "Snapshots[].SnapshotId" --output text)

for image_id in $IMAGE_IDS; do
    echo "Processing image: $image_id"
    # Example: To deregister the image, uncomment the next line
    aws ec2 deregister-image --image-id "$image_id"
    echo "Deregistered image: $image_id"

done

for snap_id in $SNAP_IDS; do
    echo "Processing snapshot: $snap_id"
    # Example: To delete the snapshot, uncomment the next line
    aws ec2 delete-snapshot --snapshot-id "$snap_id"
    echo "Deleted snapshot: $snap_id"
done
rm -f image.id
