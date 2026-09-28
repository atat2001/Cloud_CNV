package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.sun.net.httpserver.*;
import software.amazon.awssdk.services.ec2.endpoints.internal.Value;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Timestamp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import pt.ulisboa.tecnico.cnv.javassist.tools.DynamoDBTools;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;



public class LoadBalancer {

    private static final int MAX_POINTS = 10;
    private static final long SMALL_THRESHOLD = 1_720_000L;
    private static final long MEDIUM_THRESHOLD = 51_600_000L;


    private static final Map<String, Long> metricsDB = new ConcurrentHashMap<>();
    private static final Map<AwsInstance, Double> vmCpuUsage = new ConcurrentHashMap<>();

    private static final Set<AwsInstance> awsInstances = new HashSet<>();

    public static void main(String[] args) throws IOException {
        // CpuAutoScaler autoScaler = new CpuAutoScaler();
        Thread autoScaler = new Thread(new RequestHandler.CpuAutoScaler());
        autoScaler.setDaemon(true);
        autoScaler.start();

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new RequestHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Load Balancer started on port 8000");
    }

    static class RequestHandler implements HttpHandler {
        private static final int MAX_RETRY_ATTEMPTS = 3;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            System.out.println("RequestHandler handle: Received request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            String body = new String(requestBody);
            WorkloadType workloadType = extractType(exchange.getRequestURI().getPath());
            HashMap<String, String> params = extractParams(workloadType, exchange.getRequestURI().toString());
            
            // System.out.println("RequestHandler handle: Before");
            Map<String, AttributeValue> record = null;
            try{
            record = DynamoDBTools.getClosestRecords(
                    WorkloadType.toString(workloadType),
                    params.get("fixedValue"),
                    Integer.parseInt(params.get("sortedValue"))
            );
            }
            catch (Exception e) {
                System.out.println("Params: " + params);
                System.out.println("WorkloadType: " + workloadType);
                System.err.println("Error fetching record from DynamoDB: " + e.getMessage());
                String msg = "Error fetching record from DynamoDB: " + e.getMessage();
                return;
            }
            // System.out.println("RequestHandler handle: Record fetched from DynamoDB: " + record);
            long instrCount;
            try{
            if (record == null) {
                instrCount = MEDIUM_THRESHOLD + 1;
            } else if (isExactMatch(record)) {
                instrCount = Integer.parseInt(record.get("N_instructions").getN());
            } else {
                instrCount = estimateInstructions(workloadType, Integer.parseInt(params.get("sortedValue")), record);
            }
            }
            catch (Exception e) {
                System.err.println("Error estimating instructions: " + e.getMessage());
                String msg = "Error estimating instructions: " + e.getMessage();
                exchange.sendResponseHeaders(500, msg.length());
                exchange.getResponseBody().write(msg.getBytes());
                exchange.close();
                return;
            }
            System.out.println("RequestHandler handle: Estimated instructions count: " + instrCount);

            int points = computePoints(instrCount);

// Retry loop for handling VM crashes or selecting fallback
            int retryAttempts = 0;
            while (retryAttempts < MAX_RETRY_ATTEMPTS) {
                AwsInstance selectedVM = null;


                // Try selecting a VM
                synchronized (awsInstances) {
                    selectedVM = selectVM(points);
                    if(selectedVM != null){
                        synchronized (selectedVM.decomissionLock) {
                            if (selectedVM.decomissioned) {
                                System.out.println("Selected VM is decomissioned, trying again.");
                                continue;
                            }
                            System.out.println("Selected VM: " + selectedVM.instanceId + " with current load: " + selectedVM.load.get());
                            selectedVM.load.getAndAdd(points);
                        }
                    }

                }
                System.out.println("RequestHandler handle: Selected VM: " + (selectedVM != null ? selectedVM.instanceId : "None") + " for points: " + points);

                if (selectedVM == null) {
                    if (points == 1 ) {
                        String returner = "";
                        System.out.println("No VM available for< small request. Routing to Lambda.");
                        switch (workloadType) {
                            case GAME_OF_LIFE:
                                    returner = invokeGOL(params.get("sortedValue"), params.get("fixedValue"));
                                break;
                            case FIFTEEN_PUZZLE:
                                        returner = invoke15P(params.get("fixedValue"), params.get("sortedValue"));
                                break;
                            case CAPTURE_THE_FLAG:
                                        returner = invokeCTF(
                                        params.get("gridSize"),
                                        params.get("numBlueAgents"),
                                        params.get("numRedAgents"),
                                        params.get("flagPlacementType")
                                );
                                break;
                        }
                        System.out.println("Request successfully routed to Lambda.");
                        System.out.println("Request_handler handle: Response from Lambda: " + returner);

                        byte[] response = returner.getBytes();
                        exchange.sendResponseHeaders(200, response.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response);
                        }
                        catch (Exception e) {
                            System.err.println("Error sending response: " + e.getMessage());
                            String msg = "Error sending response: " + e.getMessage();
                            exchange.sendResponseHeaders(500, msg.length());
                            exchange.getResponseBody().write(msg.getBytes());
                        }
                        exchange.close();
                        return;
                    } else {
                        System.out.println("No VM available for medium/large request. Sleeping and retrying...");
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        continue; // Retry
                    }
                }



                try {
                    System.out.println("Request_handler handle: Forwarding request to VM: " + selectedVM.instanceId);
                    String vmResponse = forwardToVM(selectedVM, exchange.getRequestURI().toString());
                    System.out.println("Request_handler handle: Received response from VM: " + selectedVM.instanceId);
                    System.out.println("Request_handler handle: Response from VM: " + vmResponse);

                    byte[] response = vmResponse.getBytes();
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                    catch (Exception e) {
                        System.err.println("Error sending response: " + e.getMessage());
                        String msg = "Error sending response: " + e.getMessage();
                        exchange.sendResponseHeaders(500, msg.length());
                        exchange.getResponseBody().write(msg.getBytes());
                    }
                    System.out.println("Request_handler handle: Response sent to client.");

                    exchange.close();
                    return;

                } catch (Exception e) {
                    System.err.println("VM CRASH DETECTED: Instance " + selectedVM.instanceId + " failed to handle request!");
                    System.err.println("Error details: " + e.getMessage());
                    System.err.println("Marking VM as decommissioned and attempting recovery...");

                    synchronized (selectedVM.decomissionLock) {
                        selectedVM.decomissioned = true;
                    }

                    synchronized (awsInstances) {
                        awsInstances.remove(selectedVM);
                    }

                    retryAttempts++;

                    if (retryAttempts < MAX_RETRY_ATTEMPTS) {
                        System.out.println("Retrying request with a different VM (attempt " + (retryAttempts + 1) + "/" + MAX_RETRY_ATTEMPTS + ")");
                    } else {
                        System.err.println(" Max retry attempts reached. Request failed after " + MAX_RETRY_ATTEMPTS + " attempts.");
                        String msg = "Request failed: All VMs crashed or unavailable after " + MAX_RETRY_ATTEMPTS + " attempts.";
                        exchange.sendResponseHeaders(500, msg.length());
                        exchange.getResponseBody().write(msg.getBytes());
                        exchange.close();
                        return;
                    }
                } finally {
                    selectedVM.load.getAndAdd(-points);
                }
            }
        }

        private static int computePoints(long instr) {
            // System.out.println("computePoints: Computing points for instructions: " + instr);
            if (instr <= SMALL_THRESHOLD) return 1;
            if (instr <= MEDIUM_THRESHOLD) return 2;
            return 3;
        }

        // This method has to be called in a synchronized context to ensure thread safety
        private static AwsInstance selectVM(int points) {
            System.out.println("selectVM: Selecting VM for points: " + points);
            AwsInstance best = null;
            int minSpare = MAX_POINTS + 1;
            for (AwsInstance instance : awsInstances) {
                if (instance.decomissioned) continue;
                if (instance.load.get() + points <= MAX_POINTS) {
                    int spare = MAX_POINTS - (instance.load.get() + points);
                    if (spare < minSpare) {
                        best = instance;
                        minSpare = spare;
                    }
                }
            }
            return best;
        }

        private static String forwardToVM(AwsInstance instance, String body) throws IOException {
            return instance.forwardRequest(body);
        }


        private static boolean isExactMatch(Map<String, AttributeValue> record) {

            return record.get("match").getBOOL();
        }

        private static long estimateInstructions(WorkloadType type, int sortedValue, Map<String, AttributeValue> record) {
            System.out.println("estimateInstructions: Estimating instructions for workload type: " + type.toString() + ", sortedValue: " + sortedValue);

            switch (type) {
                case GAME_OF_LIFE:
                    return (((long) sortedValue * Integer.parseInt(record.get("N_instructions").getN())) * Integer.parseInt(record.get("N_iterations").getN()));


                case FIFTEEN_PUZZLE:
                    return (((long) sortedValue * Integer.parseInt(record.get("N_instructions").getN())) * Integer.parseInt(record.get("N_size").getN()));

                case CAPTURE_THE_FLAG:
                    return (((long) sortedValue * Integer.parseInt(record.get("N_instructions").getN())) * Integer.parseInt(record.get("N_size").getN()));

                default:
                    return MEDIUM_THRESHOLD;
            }
        }

        private static WorkloadType extractType(String path) {
            //add logic for identifying
            if (path == null) return WorkloadType.UNKNOWN;

            switch (path.toLowerCase()) {
                case "/gameoflife":
                    return WorkloadType.GAME_OF_LIFE;
                case "/fifteenpuzzle":
                    return WorkloadType.FIFTEEN_PUZZLE;
                case "/capturetheflag":
                    return WorkloadType.CAPTURE_THE_FLAG;
                default:

                    return WorkloadType.UNKNOWN;
            }
        }

        public static HashMap<String, String> extractParams(WorkloadType type, String path) {
            HashMap<String, String> params = new HashMap<>();

            // Extract query string from path
            String queryString = "";
            int queryIndex = path.indexOf('?');
            //System.out.println("extractParams: Query index in path: " + queryIndex);
            //System.out.println("extractParams: Full path: " + path);
            if (queryIndex != -1 && queryIndex < path.length() - 1) {
                //System.out.println("extractParams: Found query string in path: " + path.substring(queryIndex + 1));
                queryString = path.substring(queryIndex + 1);
            }

            // Parse query parameters
            HashMap<String, String> queryParams = new HashMap<>();
            if (!queryString.isEmpty()) {
                //System.out.println("extractParams: Parsing query string: " + queryString);
                String[] pairs = queryString.split("&");
                for (String pair : pairs) {
                    //System.out.println("extractParams: Processing pair: " + pair);
                    String[] keyValue = pair.split("=", 2);
                    //System.out.println("extractParams: Key-Value pair: " + Arrays.toString(keyValue));
                    if (keyValue.length == 2) {
                        queryParams.put(keyValue[0], keyValue[1]);
                    }
                }
            }

            // Map parameters based on workload type
            switch (type) {
                case GAME_OF_LIFE:
                    params.put("fixedValue", queryParams.get("mapFilename"));
                    params.put("sortedValue", queryParams.get("iterations"));
                    break;

                case FIFTEEN_PUZZLE:
                    params.put("fixedValue", queryParams.get("shuffles"));
                    params.put("sortedValue", queryParams.get("size"));
                    break;

                case CAPTURE_THE_FLAG:
                    params.put("fixedValue", String.format(queryParams.get("flagPlacementType") + "-" + queryParams.get("numBlueAgents") + "-" + queryParams.get("numRedAgents")));
                    params.put("sortedValue", queryParams.get("gridSize"));
                    break;

                case UNKNOWN:
                default:
                    // Return empty map for unknown types
                    break;
            }

            return params;
        }


        static class CpuAutoScaler implements Runnable {
            private static final long SLEEP_INTERVAL_MS = 10_000;
            private static boolean first_time = true;

            @Override
            public void run() {
                System.out.println("[AutoScaler] Starting CPU AutoScaler thread...");
                while (true) {
                    try {
                        if (!first_time) {
                            Thread.sleep(SLEEP_INTERVAL_MS);
                        } else {
                            first_time = false;
                        }
                        if (awsInstances.isEmpty()) {
                            spawnNewVM();
                            continue;
                        }
                        // Update CPU usage for all VMs
                        for (AwsInstance instance : awsInstances) {
                            try {
                                double cpu = fetchCpuUsage(instance);
                                vmCpuUsage.put(instance, cpu);
                            } catch (Exception e) {
                                vmCpuUsage.put(instance, 100.0);
                            }
                        }

                        // Scale out if needed
                        boolean hasLowCpu = vmCpuUsage.values().stream().anyMatch(cpu -> cpu < 75.0);
                        if (!hasLowCpu) {
                            System.out.println("No VMs with CPU usage below 75%, spawning new VM.");
                            spawnNewVM();
                        } else { // only run this if we have a machine with lower cpu than 75
                            long runningVMs = awsInstances.stream()
                                    .filter(v -> !v.decomissioned)
                                    .count();
                            long backupCpus = awsInstances.stream()
                                    .filter(v -> vmCpuUsage.getOrDefault(v, 100.0) < 75.0)
                                    .count(); // at least 2 machines less than 75

                            // Mark new VMs for decomissioning if underutilized
                            for (AwsInstance instance : awsInstances) {
                                if (instance.decomissioned) continue;

                                double cpu = vmCpuUsage.getOrDefault(instance, 100.0);

                                if (cpu < 20.0 && runningVMs >= 1 && backupCpus > 1) {
                                    backupCpus -= 1; // we are going to remove one, so we need to account for that
                                    runningVMs -= 1; // we are going to remove one, so we need to account for that
                                    // this marks the vm as decomissioned, while it has work it will do it, then it gets deleted
                                    shutdownVM(instance);
                                    System.out.println("Marked 1 instance " + " for decomissioning.");
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

        private double fetchCpuUsage(AwsInstance instance) throws IOException {
            System.out.println("[AutoScaler] Fetching CPU usage for instance: " + instance.instanceId);
            System.out.println("[AutoScaler] CpuUsage: " + instance.cpuUsage().toString());
            return instance.fetchCpuUsage();
        }

            private void spawnNewVM() {

                // If we send empty strings we get the default values from the awsinstance file
                awsInstances.add(new AwsInstance("", ""));

                System.out.println("[AutoScaler] Spawning new VM...");
            }

            private void shutdownVM(AwsInstance instance) {
                // System.out.println("[AutoScaler] Decomissioning VM: " + instance.instanceId);
                CompletableFuture.runAsync(() -> {
                    instance.decomission();
                    synchronized (instance) {
                        if (awsInstances.contains(instance)) {
                            awsInstances.remove(instance);
                        }
                    }
                    System.out.println("[AutoScaler] Shut down VM: " + instance.instanceId);
                }).exceptionally(throwable -> {
                    System.err.println("[AutoScaler] Failed to shut down VM: " + throwable.getMessage());
                    return null;
                });

                System.out.println("[AutoScaler] Started shutdown process for VM: " + instance.instanceId);
            }
        }

        public String formatJsonString(String jsonString) {
            // Remove outer quotes if they exist
            if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                jsonString = jsonString.substring(1, jsonString.length() - 1);
            }
            // Replace escaped quotes with regular quotes
            return jsonString.replace("\\\"", "\"");
        }

        public String invokeGOL(String iterations, String mapName) {
            String value = "";
            try {

                String functionName = "cnv-gameoflife-handler";
                LambdaClient awsLambda = LambdaClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();

                String json = String.format("{\"iterations\":\"%s\",\"mapFilename\":\"%s\"}", iterations, mapName);
                SdkBytes payload = SdkBytes.fromUtf8String(json);

                InvokeRequest request = InvokeRequest.builder().functionName(functionName).payload(payload).build();

                InvokeResponse res = awsLambda.invoke(request);
                // System.out.println("Raw payload: " + res.payload());
                value = formatJsonString(res.payload().asUtf8String());
                // System.out.println("\n\n\n" + value);
                try{awsLambda.close();}catch (Exception e) {
                    System.err.println("Error closing AWS Lambda client: " + e.getMessage());
                }
                
                return value;

            } catch (LambdaException e) {
                System.err.println(e.getMessage());
                System.exit(1);
                return "";
            }
        }

        public String invoke15P(String shuffles, String size) {
            String value = "";
            try {

                String functionName = "cnv-fifteenpuzzle-handler";
                LambdaClient awsLambda = LambdaClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();

                String json = String.format("{\"shuffles\":\"%s\",\"size\":\"%s\"}", shuffles, size);
                SdkBytes payload = SdkBytes.fromUtf8String(json);

                InvokeRequest request = InvokeRequest.builder().functionName(functionName).payload(payload).build();

                InvokeResponse res = awsLambda.invoke(request);
                // System.out.println("Raw payload: " + res.payload());
                value = formatJsonString(res.payload().asUtf8String());
                // System.out.println("\n\n\n" + value);
                try{awsLambda.close();}catch (Exception e) {
                    System.err.println("Error closing AWS Lambda client: " + e.getMessage());
                }
                return value;

            } catch (LambdaException e) {
                System.err.println(e.getMessage());
                System.exit(1);
                return "";
            }
        }

        public String invokeCTF(String gridSize, String numBlueAgents, String numRedAgents, String flagPlacementType) {
            String value = "";

            try {

                String functionName = "cnv-capturetheflag-handler";
                LambdaClient awsLambda = LambdaClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();

                String json = String.format("{\"gridSize\":\"%s\",\"numBlueAgents\":\"%s\",\"numRedAgents\":\"%s\",\"flagPlacementType\":\"%s\"}", gridSize, numBlueAgents, numRedAgents, flagPlacementType);
                SdkBytes payload = SdkBytes.fromUtf8String(json);

                InvokeRequest request = InvokeRequest.builder().functionName(functionName).payload(payload).build();

                InvokeResponse res = awsLambda.invoke(request);
                // System.out.println("Raw payload: " + res.payload());
                value = formatJsonString(res.payload().asUtf8String());
                // System.out.println("\n\n\n" + value);
                try{awsLambda.close();}catch (Exception e) {
                    System.err.println("Error closing AWS Lambda client: " + e.getMessage());
                }
                return value;
            } catch (LambdaException e) {
                System.err.println(e.getMessage());
                System.exit(1);
                return "";
            }
        }
    }

}


