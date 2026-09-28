## Games@Cloud

**Cloud-native, auto-scaling compute platform on AWS**, built for a Cloud Computing and Virtualization course project. It exposes CPU-intensive workloads (Capture the Flag, 15-Puzzle Solver, Conway's Game of Life) behind a REST web server and a custom load balancer that dynamically provisions and decommissions EC2 worker instances based on real-time demand.

Key technical highlights:

- **EC2 & AMIs** — worker nodes are packaged as custom Amazon Machine Images and launched/terminated programmatically via the AWS SDK, with shell tooling (`aws_scripts/`) to build images and manage instance lifecycle.
- **Custom load balancer & auto-scaler** — a Java-based load balancer (`loadbalancer/`) distributes incoming requests across a fleet of EC2 instances and implements auto-scaling logic that spins instances up or down based on load, rather than relying solely on managed AWS auto-scaling groups.
- **DynamoDB** — request metrics and instrumentation data are persisted to DynamoDB (`javassist/`) to inform scaling and load-balancing decisions.
- **CloudWatch** — instance and system metrics are published to and read from CloudWatch (`CloudWatchActions`, `CloudWatchScenario`) to drive scaling decisions and observability.
- **Bytecode instrumentation** — a Javassist-based Java agent dynamically instruments workload code to measure per-request complexity (branch counts, method execution time), enabling smarter, complexity-aware load balancing instead of naive round-robin.
- **REST web server** — a lightweight Java HTTP server exposes each workload as an API endpoint, acting as the entry point fronted by the load balancer.
- **AWS SDK for Java** — direct programmatic control of AWS resources from application code: AWS SDK v2 (EC2, CloudWatch and Lambda clients) drives the load balancer's instance provisioning and metrics, while AWS SDK v1 (DynamoDB client) powers metrics persistence from the Javassist agent.
- **AWS CLI batch scripting** — a suite of Bash scripts (`aws_scripts/`) automates the full infrastructure lifecycle using the AWS CLI: launching monitored EC2 instances, installing/updating the web server on boot, baking custom AMIs for both worker and load-balancer nodes, and tearing down instances/AMIs/snapshots in bulk. A separate set of scripts (`cf.sh`, `f.sh`, `gol.sh`, `test3.sh`) drives `curl`-based load/integration testing against the deployed endpoints.

This project demonstrates end-to-end design and implementation of a horizontally-scalable distributed system on AWS, including infrastructure automation, custom load-balancing/auto-scaling algorithms, and cloud-native observability and persistence.

This project contains three sub-projects:

1. `capturetheflag` - the Capture the Flag workload
2. `fifteenpuzzle` - the 15-Puzzle Solver workload
3. `gameoflife` - the Conway's Game of Life workload
4. `webserver` - the web server exposing the functionality of the workloads

Refer to the `README.md` files of the sub-projects to get more details about each specific sub-project.

### How to build everything

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`

## Folder structure and content

LoadBlancer module contains Loadbalancer, Auto Scaler, as well as some helper classes to manage instances.
All DynamoDB related classes are in the javaassist tools folder (this was done for the convenience of not having to setup any other pom configs).
Aws scripts contain helpers to create ami, launch vm's and deleting VM's. These dont work unless you have the config set up.
