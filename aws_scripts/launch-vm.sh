#!/bin/bash

source config.sh
INSTANCE_ID=$(aws ec2 run-instances \
    --image-id "ami-074e262099d145e90" \
    --instance-type "t2.micro" \
    --key-name $AWS_KEYPAIR_NAME \
    --network-interfaces "{\"AssociatePublicIpAddress\":true,\"DeviceIndex\":0,\"Groups\":[\"$AWS_SECURITY_GROUP\"]}" \
    --monitoring '{"Enabled":true}' \
    --credit-specification '{"CpuCredits":"standard"}' \
    --tag-specifications '{"ResourceType":"instance","Tags":[{"Key":"Name","Value":"project"}]}' \
    --metadata-options '{"HttpEndpoint":"enabled","HttpPutResponseHopLimit":2,"HttpTokens":"required"}' \
    --private-dns-name-options '{"HostnameType":"ip-name","EnableResourceNameDnsARecord":true,"EnableResourceNameDnsAAAARecord":false}' \
    --count "1" \
    --query "Instances[0].InstanceId" \
    --region $AWS_DEFAULT_REGION \
    --output text)


MAX_RETRIES=30
RETRY=0

while [ $RETRY -lt $MAX_RETRIES ]; do
    PUBLIC_IP=$(aws ec2 describe-instances \
        --instance-ids "$INSTANCE_ID" \
        --query "Reservations[0].Instances[0].PublicIpAddress" \
        --output text)

    if [ "$PUBLIC_IP" != "None" ]; then
        echo "Public IP assigned: $PUBLIC_IP"
        break
    fi
    

    echo "Waiting... ($((RETRY + 1))/$MAX_RETRIES)"
    sleep 2
    RETRY=$((RETRY + 1))
done

if [ "$PUBLIC_IP" == "None" ]; then
    echo "Timed out waiting for public IP."
    $DIR/terminate_ec2.sh
fi

echo "$INSTANCE_ID $PUBLIC_IP" >> instances_to_install.id
echo "EC2 Instance launched with ID: $INSTANCE_ID"
echo "Public IP: $PUBLIC_IP"
