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
        mvn clean package
        cd ../
        mvn clean install
        cd webserver/
        mvn clean install

        sudo su
        cat <<EOF > /etc/systemd/system/rc-local.service
[Unit]
Description=/etc/rc.local Compatibility
ConditionPathExists=/etc/rc.local
[Service]
Type=forking
ExecStart=/etc/rc.local start
TimeoutSec=0
StandardOutput=tty
RemainAfterExit=yes
SysVStartPriority=99
[Install]
WantedBy=multi-user.target
EOF
        cat <<EOF > /etc/rc.local
#!/bin/sh -e
cd /home/ec2-user/cnv25-g07
java -cp webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -Xbootclasspath/a:javassist/target/JavassistWrapper-1.0-jar-with-dependencies.jar -javaagent:webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv.capturetheflag,pt.ulisboa.tecnico.cnv.fifteenpuzzle,pt.ulisboa.tecnico.cnv.gameoflife:output pt.ulisboa.tecnico.cnv.webserver.WebServer &> /tmp/webserver.log
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