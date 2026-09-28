package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.ArrayList;
import java.util.List;


public class DynamoDBFlusher implements Runnable {
    private static final int BATCH_SIZE = 25; // DynamoDB max batch size
    private static final int FLUSH_INTERVAL_MS = 5000;

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(FLUSH_INTERVAL_MS);

                // System.out.println("DEBUG: waking up DDB Flusher ...");

                List<String[]> toFlush = new ArrayList<>();
                RecordQueue.QUEUE.drainTo(toFlush, BATCH_SIZE);

                if (!toFlush.isEmpty()) {
                    for (String[] args : toFlush) {
                        // Add your hash-based ID generation here
                        // System.out.println("DEBUG - flushing item: " + args[0] + "/" + args[1]);
                        DynamoDBTools.upsert_item(args);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in DynamoDBFlusher:");
                e.printStackTrace();
            }
        }
    }
}

