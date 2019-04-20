import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TTransportFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TSSLTransportFactory.TSSLTransportParameters;

import java.io.*;
import java.util.*;

public class Server {
    public static ServerHandler handler;
    public static Client2Server.Processor<ServerHandler> processor;
    private static String[] host_add;
    private static int[] port_num;
    // serv_file cn_file1 cn_file2 cn_file3  cn_file4
    // 0 1 2 3 4

    public static void main(String[] args) {
        // assume the command is: Java Server serv_file cn_file1 cn_file2 cn_file3 cn_file4
        int argc = args.length;
        if (argc < 2) {
            System.out.println("Wrong number of arguments!");
            return;
        }

        host_add = new String[argc];
        port_num = new int[argc];
        try {
            for (int i = 0; i < argc; i++) {
                BufferedReader input = new BufferedReader(new FileReader(args[i]));
                host_add[i] = input.readLine();
                port_num[i] = Integer.parseInt(input.readLine());
                input.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //client to server
        handler = new ServerHandler(host_add, port_num);
        processor = new Client2Server.Processor<ServerHandler>(handler);
        Thread thread_serv = new Thread(new Runnable() {
            public void run() {
                c_to_s(processor, port_num[0]);
                //host_add[0] seems to be useless
            }
        });
        thread_serv.start();
    }

    public static void c_to_s(Client2Server.Processor<ServerHandler> processor, int _port){
        try {
            //Create Thrift server socket
            TServerTransport ST = new TServerSocket(_port);
            TTransportFactory factory = new TFramedTransport.Factory();
            //Create service request handler
            ServerHandler handler = new ServerHandler(host_add, port_num);
            processor = new Client2Server.Processor<ServerHandler>(handler);
            //Set server arguments
            TServer.Args args = new TServer.Args(ST);
            args.processor(processor);  //Set handler
            args.transportFactory(factory); //Set FramedTransport (for performance)
            //Thread Server to process the request form client
            TServer server = new TSimpleServer(args);
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
