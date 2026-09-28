Copy mykeypair.pem to this locations
update config with your credentials

Launch vm: launches a free vm with cloudwatch enabled.

Install vm: instals everything and ensures vm runs the most up to date webserver on boot(always run launch vm first).

create-image: creates an ami with the vm specified above, that launches the server on boot.
create-lb-image: creates an ami with the vm specified above, that launches the lb on boot.

terminate ec2: Deletes EVERY ec2 instance on the account. CAREFUL!!!
remove_images: Deletes EVERY AMI and Snapshot on the account. Again CAREFUL!!!

terminate_all: does both programs above.