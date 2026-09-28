#!/bin/bash
source config.sh

while read -r id ip; do
    echo "ID: $id, IP: $ip"
    echo "ID: $id, IP: $ip"
    ./ensure_git_up_to_date.sh

    MAX_RETRIES=30
    RETRY=0
    while [ $RETRY -lt $MAX_RETRIES ]; do
        Status=$(aws ec2 describe-instance-status \
            --instance-ids "$id" \
            --query "InstanceStatuses[0].InstanceStatus.Status" \
            --output text)
        echo "status is: $Status"
        if [ "$Status" == "ok" ]; then
            echo "Status ok: $Status"
            break
        fi
        

        echo "Waiting... ($((RETRY + 1))/$MAX_RETRIES)"
        sleep $((2 + (RETRY / 2)))
        RETRY=$((RETRY + 1))
    done

    scp -o StrictHostKeyChecking=no -i mykeypair.pem -r "$REPO_DIR" ec2-user@$ip:
    ssh -o StrictHostKeyChecking=no -i mykeypair.pem ec2-user@$ip <<'OUTER_EOF'
        sudo yum update -y
        sudo yum install java-11-amazon-corretto-devel.x86_64 -y
        sudo yum install maven -y
        cd cnv25-g07/
        cd javassist/
        mvn clean install
        cd ../loadbalancer/
        mvn clean install
        cd ../
        mvn clean install
        sudo su
        cd aws_scripts/
        source config.sh
        cat <<EOF > /etc/systemd/system/rc-local.service
[Unit]
Description=CNV LoadBalancer Service
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user/cnv25-g07
EnvironmentFile=/home/ec2-user/cnv25-g07/aws_scripts/config
ExecStart=/usr/bin/mvn exec:java -pl loadbalancer -Dexec.mainClass="pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer"
Restart=on-failure
StandardOutput=append:/tmp/loadbalancer.log
StandardError=append:/tmp/loadbalancer.log

[Install]
WantedBy=multi-user.target
EOF
        cat <<EOF > /etc/rc.local
#!/bin/sh -e
cd /home/ec2-user/cnv25-g07
mvn exec:java -pl loadbalancer -Dexec.mainClass="pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer" &> /tmp/loadbalancer.log
EOF
        sudo chmod +x /etc/rc.local
        sudo systemctl enable rc-local.service
        exit
OUTER_EOF
done < instances_to_install.id

cat instances_to_install.id >> instance.id
rm -f instances_to_install.id
echo "Software installed on all instances listed in instances_to_install.id"



#
#cd webserver/
#        mvn clean package