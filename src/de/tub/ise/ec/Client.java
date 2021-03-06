package de.tub.ise.ec;

import de.tub.ise.ec.kv.KeyValueInterface;
import de.tub.ise.hermes.Request;
import de.tub.ise.hermes.Response;
import de.tub.ise.hermes.Sender;
import org.apache.commons.lang3.time.StopWatch;
import java.io.FileWriter;
import java.io.IOException;


public class Client {
    private Sender sender;
    private Request req;
    private Response res;
    private long Latency;
    private long  Staleness;
    private static final String ADD = "create";
    private static final String DEL = "delete";
    private static final String SET = "update";

    //Delimiter used in CSV file
    private static final String COMMA_DELIMITER = ",";
    private static final String NEW_LINE_SEPARATOR = "\n";
    //CSV file header
   // private static final String FILE_HEADER = "Latency,Staleness\n";


    /**
     *  Initiate the client
     * @param host: the server to communicate with
     * @param port: the port to communicate through
     */
    public Client(String host, int port ){
        sender = new Sender(host, port);
        req=null;
        res=null;
        Latency=0;
        Staleness=0;
    }

    /**
     * Create request to the server
     * @param message: the message to the server
     * @param target : the target server handling the request
     * @param sender: the client id sending the request
     * @return
     */
    public Request createRequest(Command message, String target, String sender){

        req = new Request(message, target, sender);
        return req;
    }

    /**
     * send the request to the master server,
     * append benchmark results to the csv file
     * and Print the response
     */
    public void sendReqclient() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        res = sender.sendMessage(req, 5000);
        if(!res.responseCode()) System.out.println("Response: Failed to connect to server. "+res.getResponseMessage());
        else {
            stopWatch.stop();
            Latency = stopWatch.getTime();
            Command c = (Command) res.getItems().get(0);
            if (c.getMode().equals("Sync")) {
                if (c.getSlaveTimestamp() != null && c.getMasterTimestamp() != null)
                    Staleness = c.getSlaveTimestamp().getTime() - c.getMasterTimestamp().getTime();
                String fileName = ".//Sync_Benchmark.csv";
                writeCsvFile(fileName);
            } else {
                String fileName = ".//Async_latency.csv";
                writeCsvFile(fileName);
            }
            if (c.getOperation().equals("read"))
                System.out.println("Response:\n" + c.getResponseMsg() + ": " + c.getValue());
            else System.out.println("Response:\n" + c.getResponseMsg());
        }
    }

    /**
     *  send the request to slave and get the
     *  serializable object from the response
     * @return Serializable object Command
     */
    public Command sendReqMaster(KeyValueInterface store) {
        Command c=null;
        res = sender.sendMessage(req, 5000);
        if(res.responseCode())
        {c = (Command)res.getItems().get(0);
        }
        else{revert(req,store);
          c.setResponseMsg("Failed to execute the operation");
        }
        return c;
    }

    /**
     *  Send the request asynchronously
     * @throws InterruptedException : Interrupted Exception
     */
    public void sendReqMasterAsync(KeyValueInterface store) {

        AsyncRequest echoAsyncCallback = new AsyncRequest();
        boolean received = sender.sendMessageAsync(req, echoAsyncCallback);

        while (true) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (echoAsyncCallback.getResponse() != null) break;
        }
        if(echoAsyncCallback.getResponse().responseCode()){
        Command c = (Command)echoAsyncCallback.getResponse().getItems().get(0);
         Staleness =c.getSlaveTimestamp().getTime()-c.getMasterTimestamp().getTime();
         String fileName = ".//Async_Staleness.csv";
         writeCsvFile(fileName);}
         else{
             revert(req,store);
        }
    }

    /**
     * Revert the operation executed on master
     */
    public void revert(Request req,KeyValueInterface store){
        Command c =(Command) req.getItems().get(0);
        switch (c.getOperation()){
            case  ADD:
                store.store(c.getKey(),c.getValue());
                break;
            case DEL:
                store.delete(c.getKey());
                break;
            case  SET:
                store.delete(c.getKey());
                store.store(c.getKey(), c.getOldValue());
                break;
        }
    }

    /**
     * Write a csv file in the given path
     * @param fileName
     */
    private  void writeCsvFile(String fileName) {
        FileWriter fileWriter = null;
        try{
            fileWriter = new FileWriter(fileName,true);

            fileWriter.append(String.valueOf(Latency));
            fileWriter.append(COMMA_DELIMITER);
            fileWriter.append(String.valueOf(Staleness));
            fileWriter.append(NEW_LINE_SEPARATOR);


        }
     catch(Exception e){
         System.out.println("Error in CsvFileWriter !!!");
         e.printStackTrace();
     }finally{
         try{fileWriter.flush();
             fileWriter.close();
         }
         catch(IOException e){
             System.out.println("Error while  flushing/closing fileWriter !!!");
             e.printStackTrace();
         }
     }
    }
    }
