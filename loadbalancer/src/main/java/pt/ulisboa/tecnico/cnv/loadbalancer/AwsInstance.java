//snippet-sourcedescription:[CreateInstance.java demonstrates how to create an Amazon Elastic Compute Cloud (Amazon EC2) instance.]
//snippet-keyword:[AWS SDK for Java v2]
//snippet-service:[Amazon EC2]

/*
   Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
   SPDX-License-Identifier: Apache-2.0
*/

package pt.ulisboa.tecnico.cnv.loadbalancer;

// snippet-start:[ec2.java2.create_instance.main]
// snippet-start:[ec2.java2.create_instance.import]
// snippet-start:[ec2.java2.describe_tags.main]
// snippet-start:[ec2.java2.describe_tags.import]
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.DescribeTagsResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.DescribeTagsRequest;
// snippet-start:[ec2.java2.monitor_instance.main]
// snippet-start:[ec2.java2.monitor_instance.import]
import software.amazon.awssdk.services.ec2.model.MonitorInstancesRequest;
import software.amazon.awssdk.services.ec2.model.UnmonitorInstancesRequest;
// snippet-start:[ec2.java2.create_instance.main]
// snippet-start:[ec2.java2.create_instance.import]
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
// ...existing imports...
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;

import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;

import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Timestamp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
// ...existing code...

// ...existing code...
// snippet-end:[ec2.java2.create_instance.import]

/**
 * Before running this Java V2 code example, set up your development environment, including your credentials.
 *
 * For more information, see the following documentation topic:
 *
 * https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/get-started.html
 *
 * This code example requires an AMI value. You can learn more about this value by reading this documentation topic:
 *
 * https://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/AMIs.html
 */
public class AwsInstance {

    public final Region region;
    public boolean decomissioned;
    public boolean terminated = false;
    private Ec2Client ec2Client;
    public String instanceId = "";
    public String name = "CNV-Image";
    public String amiId = "ami-0dce7d0ecfc3b48d1";   
    public String state = "";
    public String ipv4 = "";
    public AtomicInteger load = new AtomicInteger(0);
    public final Object decomissionLock = new Object();
    public Timestamp lastRequestTime = new Timestamp(0);
    // Create an instance of CloudWatchActions
    private final CloudWatchActions cloudWatchActions = new CloudWatchActions();

    
    
    private AtomicInteger cloudwatchCounter = new AtomicInteger(0);

    public AwsInstance(String name, String amiId){
        if(name != "" && amiId != ""){
            this.name = name;
            this.amiId = amiId;
        }
        
        // Verify credentials exist
        if ((System.getenv("AWS_ACCESS_KEY_ID") == null) || System.getenv("AWS_SECRET_ACCESS_KEY") == null || System.getenv("AWS_REGION") == null) {
            throw new IllegalStateException("AWS credentials not found in environment variables");
        }
        this.region = Region.of(System.getenv("AWS_REGION"));
        try{
            try{
                System.out.println("Creating an EC2 instance with name: " + this.name + " and AMI ID: " + this.amiId);
                
                createInstance(this.name, this.amiId);
                this.decomissioned = false;
            }
            catch (Exception e) {
                System.err.println("Error 1: " + e.getMessage());
                System.exit(1);
            }

            try{
        System.out.println("Describing the instance...");
        describeInstance();
            }
            catch (Exception e) {
                System.err.println("Error 2: " + e.getMessage());
                System.exit(1);
            }
            try{
        System.out.println("Enabling monitoring for the instance...");
        monitorInstance();
            }
            catch (Exception e) {
                System.err.println("Error 3: " + e.getMessage());
                System.exit(1);
            }
            try{
        System.out.println("Cpu usage: ");
        //cpuUsage();
            }
            catch (Exception e) {
                System.err.println("Error 4: " + e.getMessage());
                System.exit(1);
            }
        }
        catch (Exception e) {
            System.err.println("Error creating instance: " + e.getMessage());
            System.exit(1);
        }
        // terminateEC2Async();
    }

    public void createInstance(String name, String amiId) {
        /*final String usage = """

            Usage:
               <name> <amiId>

            Where:
               name - An instance name value that you can obtain from the AWS Console (for example, ami-xxxxxx5c8b987b1a0).\s
               amiId - An Amazon Machine Image (AMI) value that you can obtain from the AWS Console (for example, i-xxxxxx2734106d0ab).\s
            """;*/
        try{
            this.ec2Client = Ec2Client.builder()
                .region(this.region)
                .build();

            this.instanceId = createEC2Instance(this.ec2Client, name, amiId);
        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        System.out.println("The Amazon EC2 Instance ID is " + this.instanceId);
        this.ec2Client.close();
    }

    public String createEC2Instance(Ec2Client ec2, String name, String amiId) {
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
            .imageId(amiId)
            .instanceType(InstanceType.T2_MICRO)
            .securityGroupIds("sg-01b4f2dad1f4ca225")       
            .keyName("mykeypair")
            .maxCount(1)
            .minCount(1)
            .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();

        try {
            System.out.printf("Successfully started EC2 Instance %s based on AMI %s", instanceId, amiId);
            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }


    public void monitorInstance() {
        if(this.instanceId == "") {
            System.out.println("Instance ID is not set. Please create an instance first.");
            return;
        }
        Ec2Client ec2 = Ec2Client.builder()
            .region(this.region)
            .build();
        MonitorInstancesRequest request = MonitorInstancesRequest.builder()
            .instanceIds(this.instanceId)
            .build();

        ec2.monitorInstances(request);
        ec2.close();
        System.out.printf("Successfully enabled monitoring for instance %s", instanceId);
    }
    // snippet-end:[ec2.java2.monitor_instance.main]


    // snippet-start:[ec2.java2.monitor_instance.stop]
    public void unmonitorInstance() {
        if(this.instanceId == "") {
            System.out.println("Instance ID is not set. Please create an instance first.");
            return;
        }
        Ec2Client ec2 = Ec2Client.builder()
            .region(this.region)
            .build();
        UnmonitorInstancesRequest request = UnmonitorInstancesRequest.builder()
            .instanceIds(instanceId)
            .build();

        ec2.unmonitorInstances(request);
        ec2.close();
        System.out.printf("Successfully disabled monitoring for instance %s", instanceId);
    }



    public void describeInstance() {
        Ec2Client ec2 = Ec2Client.builder()
            .region(region)
            .build();

        describeEC2Instances(ec2);
        ec2.close();
    }

    public void describeEC2Instances(Ec2Client ec2) {
        // if less than half a sec has passed dont do it to avoid spamming
        if(System.currentTimeMillis() - this.lastRequestTime.getTime() < 500) {
            return;
        }
        else {
            this.lastRequestTime = new Timestamp(System.currentTimeMillis());
        }
        String nextToken = null;
        try {
            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(this.instanceId).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);
                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        System.out.println("Instance Id is " + instance.instanceId());
                        // System.out.println("Image id is " + instance.imageId());
                        // System.out.println("Instance type is " + instance.instanceType());
                        System.out.println("Instance state name is " + instance.state().name());
                        // System.out.println("monitoring information is " + instance.monitoring().state());
                        // System.out.println("Instance public DNS is " + instance.publicDnsName());   
                        System.out.println("Instance public IP is " + instance.publicIpAddress());
                        this.state = instance.state().name().toString();
                        this.ipv4 = instance.publicIpAddress() != null ? instance.publicIpAddress() : "";
                    }
                }
                
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorCode());
            System.exit(1);
        }
    }


    
    public void decomission(){
        synchronized (this) {
            this.decomissioned = true;
            while(!this.terminated) {
                synchronized(this.decomissionLock){
                    if(this.load.get() == 0){
                            System.out.println("Decomission: load ended instance: " + this.instanceId);
                            CompletableFuture<Object> terminationFuture = terminateEC2Async();
                            terminationFuture.join(); // Wait for the termination to complete
                            System.out.println("Decomission: Instance " + this.instanceId + " has been terminated.");
                            return; // Exit the loop and method after termination
                    }
                }
                System.out.println("Decomission: Instance " + this.instanceId + " is still under load, waiting to decommission.");
                try {
                    Thread.sleep(1000); // Wait for 1 second before checking again
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    throw new RuntimeException("Thread was interrupted while waiting for instance to be ready.", e);
                }
                }
            }        
        }


    /**
     * Terminates an EC2 instance asynchronously and waits for it to reach the terminated state.
     *
     * @param instanceId the ID of the EC2 instance to terminate
     * @return a {@link CompletableFuture} that completes when the instance has been terminated
     * @throws RuntimeException if there is no response from the AWS SDK or if there is a failure during the termination process
     */
    private CompletableFuture<Object> terminateEC2Async() {
        TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
            .instanceIds(this.instanceId)
            .build();
        Ec2AsyncClient asyncClient = Ec2AsyncClient.builder()
            .region(this.region)
            .build();
        CompletableFuture<TerminateInstancesResponse> responseFuture = asyncClient.terminateInstances(terminateRequest);
        return responseFuture.thenCompose(terminateResponse -> {
            if (terminateResponse == null) {
                throw new RuntimeException("No response received for terminating instance " + this.instanceId);
            }
            System.out.println("Going to terminate an EC2 instance and use a waiter to wait for it to be in terminated state");
            return asyncClient.waiter()
                .waitUntilInstanceTerminated(r -> r.instanceIds(this.instanceId))
                .thenApply(waiterResponse ->{this.terminated = true; return null;});
        }).exceptionally(throwable -> {
            // Handle any exceptions that occurred during the async call
            throw new RuntimeException("Failed to terminate EC2 instance: " + throwable.getMessage(), throwable);
        });
    }

    public String forwardRequest(String body) throws IOException {
        while(this.state == "pending"){
            try {
                describeInstance();
                System.out.println("Instance is still pending, waiting for it to be ready...");
                Thread.sleep(1000); // Wait for 1 second before checking the state again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                throw new IOException("Thread was interrupted while waiting for instance to be ready.", e);
            }
        }
        if(this.state == "running"){
            ;
        }
        else if(this.state == "terminated") {
            throw new IOException("Instance is terminated, cannot forward request.");
        } else if(this.state == "stopped") {
            throw new IOException("Instance is stopped, cannot forward request.");
        } else if(this.ipv4 == "") {
            throw new IOException("Instance does not have a public IP address.");
        }
        else {
            throw new IOException("Instance is in an unknown state: " + this.state);
        }
        // http://$HOST:$PORT/capturetheflag\?gridSize=$gridSize\&numBlueAgents=$numBlueAgents\&numRedAgents=$numRedAgents\&flagPlacementType=$flagPlacementType
        URL url = new URL("http://" + this.ipv4 + ":8000" + body);
        System.out.println("Forwarding request to: " + url.toString());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        
        // Set standard headers (like curl does)
        con.setRequestProperty("User-Agent", "Java-http-client/1.0");
        con.setRequestProperty("Accept", "*/*");
        con.setRequestMethod("GET");
        
        // Remove these lines - don't write to body for GET requests
        // con.setDoOutput(true);
        // con.getOutputStream().write(body.getBytes());


        // Add before sending request
        System.out.println("Request details:");
        System.out.println("URL: " + url.toString());
        System.out.println("Method: " + con.getRequestMethod());
        con.getRequestProperties().forEach((key, value) -> 
        System.out.println("Header - " + key + ": " + value));

        // Add after getting response
        System.out.println("Response code: " + con.getResponseCode());
        System.out.println("Response message: " + con.getResponseMessage());


        // Read the response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            return in.lines().reduce("", (a, b) -> a + b);
        }
    }

    public Double fetchCpuUsage(){
        return cpuUsage();
    }
        /*HttpURLConnection con = null;
        URL url = null;
        try{
            url = new URL("http://" + this.ipv4 + ":9001/metrics");
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
        }
        catch (Exception e) {
            System.err.println("Error getting metrics: " + e.getMessage());
            return null;
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String line = in.readLine();
            return Double.parseDouble(line.trim());
        }
        catch (Exception e) {
            System.err.println("Error fetching CPU usage: " + e.getMessage());
            return null;
        }
    }*/

    public Double cpuUsage(){
        String metricName = "CPUUtilization";
        String metricNamespace = "AWS/EC2";
        String metricOption = "Average";
        Dimension myDimension = Dimension.builder()
            .name("InstanceId")
            .value(this.instanceId)
            .build();
        String five_minuteAgo = Instant.now().minusSeconds(300).toString();
        CompletableFuture<GetMetricStatisticsResponse> returner = cloudWatchActions.getAndDisplayMetricStatisticsAsync(metricNamespace, metricName, metricOption,five_minuteAgo, myDimension);
        GetMetricStatisticsResponse metrics = returner.join(); // Wait for the async call to complete
        System.out.println("cpuUsage: Metrics: " + metrics);
        // Extract CPU value from datapoints
        List<Datapoint> datapoints = metrics.datapoints();
        if (datapoints != null && !datapoints.isEmpty()) {
            // Sort by timestamp to get most recent
            Datapoint latest = datapoints.stream()
                .max((d1, d2) -> d1.timestamp().compareTo(d2.timestamp()))
                .get();
            return latest.average();
        }
        
        return 0.0; // No datapoints available
    }


}

