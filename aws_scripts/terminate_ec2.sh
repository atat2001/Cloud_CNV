#!/bin/bash

source config.sh

aws ec2 terminate-instances --region $AWS_DEFAULT_REGION --instance-ids $(aws ec2 describe-instances --filters  "Name=instance-state-name,Values=pending,running,stopped,stopping" --query "Reservations[].Instances[].[InstanceId]" --output text | tr '\n' ' ')
rm -f instances_to_install.id
rm -f instance.id
echo "All EC2 instances terminated and instance.id file removed."