package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordQueue {
    public static final BlockingQueue<String[]> QUEUE = new LinkedBlockingQueue<>();    
}
