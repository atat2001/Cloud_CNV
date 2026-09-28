package pt.ulisboa.tecnico.cnv.javassist.tools;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.regions.Regions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;


public class DynamoDBTools {

    public static void upsert_item(String[] args) {

        final String USAGE = "\n" +
                "Usage:\n" +
                "    PutItem <table> <name> [field=value ...]\n\n" +
                "Where:\n" +
                "    table    - the table to put the item in.\n" +
                "    name     - a name to add to the table. If the name already\n" +
                "               exists, its entry will be updated.\n" +
                "Additional fields can be added by appending them to the end of the\n" +
                "input.\n\n" +
                "Example:\n" +
                "    PutItem Cellists Pau Language=ca Born=1876\n";

        if (args.length < 2) {
            System.out.println(USAGE);
            System.exit(1);
        }

        String table_name = args[0];
        ArrayList<String[]> extra_fields = new ArrayList<String[]>();

        // any additional args (fields to add to database)?
        for (int x = 1; x < args.length; x++) {
            String[] fields = args[x].split("=", 2);
            if (fields.length == 2) {
                extra_fields.add(fields);
            } else {
                System.out.format("Invalid argument: %s\n", args[x]);
                System.out.println(USAGE);
                System.exit(1);
            }
        }

        System.out.format("Adding \"%s\" to \"%s\"\n", args[1], table_name);
        if (extra_fields.size() > 0) {
            System.out.println("Additional fields:");
            for (String[] field : extra_fields) {
                System.out.format("  %s: %s\n", field[0], field[1]);
            }
        }

        HashMap<String, AttributeValue> item_values = new HashMap<String, AttributeValue>();

        for (String[] field : extra_fields) {
            if (field[0].startsWith("N_")) {
                item_values.put(field[0], new AttributeValue().withN(field[1]));
            }
            else {
                item_values.put(field[0], new AttributeValue(field[1]));
            }
        }

        // AWSCredentials creds = new DefaultAWSCredentialsProviderChain().getCredentials();
        // DefaultAwsRegionProviderChain regionProvider = new DefaultAwsRegionProviderChain();
        // System.out.println("✅ Access Key: " + creds.getAWSAccessKeyId());
        // System.out.println("✅ Access Key (Secret): " + creds.getAWSSecretKey());
        // System.out.println("✅ Region: " + regionProvider.getRegion());

        AmazonDynamoDB ddb = null;
        try {
            ddb = AmazonDynamoDBClientBuilder.standard()
                        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
        "https://dynamodb.eu-west-3.amazonaws.com", "eu-west-3"))
                        .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                        .build();
        } catch (Exception e) {
            System.err.println("Error creating DynamoDB client:");
            e.printStackTrace();
        }

        try {
            ddb.putItem(table_name, item_values);
        } catch (ResourceNotFoundException e) {
            System.err.format("Error: The table \"%s\" can't be found.\n", table_name);
            System.err.println("Be sure that it exists and that you've typed its name correctly!");
            System.exit(1);
        } catch (AmazonServiceException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error:");
            e.printStackTrace();
        }
        
        System.out.println("Done!");
    }

    public static Map<String, AttributeValue> getClosestRecords(String table_name, String part_key, int sort_key) {

        AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "https://dynamodb.eu-west-3.amazonaws.com", "eu-west-3"))
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();

        System.out.format("Retrieving item \"%s-%d\" from \"%s\"\n",
                part_key, sort_key, table_name);
        
        String part_key_col = null;
        String sort_key_col = null;
        if (table_name.equals("GoL")) {
            part_key_col = "map_name";
            sort_key_col = "N_iterations";
        }
        else if (table_name.equals("15P")) {
            part_key_col = "shuffles";
            sort_key_col = "N_size";
        }
        else if (table_name.equals("CTF")) {
            part_key_col = "params";
            sort_key_col = "N_size";
        }

        try {
            // 1. Get exact match
            HashMap<String, AttributeValue> key_to_get = new HashMap<String, AttributeValue>();

            // Building request
            key_to_get.put(part_key_col, new AttributeValue(part_key));
            key_to_get.put(sort_key_col, new AttributeValue().withN(String.valueOf(sort_key)));
            GetItemRequest request = new GetItemRequest()
                    .withKey(key_to_get)
                    .withTableName(table_name);

            // Sending request to DynamoDB
            Map<String, AttributeValue> returned_item = ddb.getItem(request).getItem();
            if (returned_item != null) {
                returned_item.put("match", new AttributeValue().withBOOL(true));
                return returned_item;
            }

            // 2. Get closest lower values
            QueryRequest lowerQuery = new QueryRequest()
                    .withTableName(table_name)
                    .withKeyConditionExpression(String.format(
                            "%s = :fixed_value AND %s < :ranged_value",
                            part_key_col,
                            sort_key_col
                    ))
                    .withExpressionAttributeValues(Map.of(
                            ":fixed_value", new AttributeValue().withS(part_key),
                            ":ranged_value", new AttributeValue().withN(String.valueOf(sort_key))
                    ))
                    .withScanIndexForward(false) // descending
                    .withLimit(1); // closest lower
            QueryResult lowerResult = ddb.query(lowerQuery);
            Map<String, AttributeValue> obj1 = null;
            if (!lowerResult.getItems().isEmpty()) {
                obj1 = lowerResult.getItems().get(0);
            }

            // 3. Get closest upper values
            QueryRequest upperQuery = new QueryRequest()
                    .withTableName(table_name)
                    .withKeyConditionExpression(String.format(
                            "%s = :fixed_value AND %s > :ranged_value",
                            part_key_col,
                            sort_key_col
                    ))
                    .withExpressionAttributeValues(Map.of(
                            ":fixed_value", new AttributeValue().withS(part_key),
                            ":ranged_value", new AttributeValue().withN(String.valueOf(sort_key))
                    ))
                    .withScanIndexForward(true) // ascending
                    .withLimit(1); // closest upper
            QueryResult higherResult = ddb.query(upperQuery);
            Map<String, AttributeValue> obj2 = null;
            if (!higherResult.getItems().isEmpty()) {
                obj2 = higherResult.getItems().get(0);
            }

            if (obj1==null && obj2==null) {
                // System.out.println("DEBUG: no values");
                return null;
            }
            else if (obj1==null) {
                // System.out.println("DEBUG: upper value");
                obj2.put("match", new AttributeValue().withBOOL(false));
                return obj2;
            }
            else if (obj2==null) {
                // System.out.println("DEBUG: lower value");
                obj1.put("match", new AttributeValue().withBOOL(false));
                return obj1;
            }
            else {
                int dist1 = sort_key - Integer.valueOf(obj1.get(sort_key_col).getN().toString());
                int dist2 = Integer.valueOf(obj2.get(sort_key_col).getN().toString()) - sort_key;
                // System.out.format("DEBUG values: %d-%d", dist1, dist2);
                if (dist1 <= dist2) {
                    obj1.put("match", new AttributeValue().withBOOL(false));
                    return obj1;
                }
                else {
                    obj2.put("match", new AttributeValue().withBOOL(false));
                    return obj2;
                }
            }

        } catch (Exception e) {
            System.err.println("Error querying DynamoDB:");
            e.printStackTrace();
        }

        return null;
    }

    public static String hashKey(String data) {
        try {
            // System.out.println("Debug: "+data);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }
    
}
