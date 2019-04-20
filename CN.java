import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.server.TThreadPoolServer.Args;
import org.apache.thrift.transport.TTransportFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

import java.io.*;
import java.util.*;

public class CN {
    public static CNHandler handler;
    public static Server2CN.Processor<CNHandler> processor;
    public static String host_add = null;
    public static int port_num = -1;
    public static double Load_P = 0.0;

    private static Set<String> positive = new HashSet<String>();
    private static Set<String> negative = new HashSet<String>();

    private static void init_set() {
        try {
            BufferedReader input = new BufferedReader(new FileReader("data/positive.txt"));
            String word = null;
            while (null != (word = input.readLine())) {
                positive.add(word);
            }
            input.close();
            input = new BufferedReader(new FileReader("data/negative.txt"));
            while (null != (word = input.readLine())) {
                negative.add(word);
            }
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        int argc = args.length;
        if (argc < 1) {
            System.out.println("Wrong number of arguments!");
            return;
        }

        String cfg_file = args[0];
        try {
            BufferedReader input = new BufferedReader(new FileReader(cfg_file));
            host_add = input.readLine();
            port_num = Integer.parseInt(input.readLine());
            Load_P = Double.parseDouble(input.readLine());
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get Compute Node's IP address and port number

        init_set();
        try {
            handler = new CNHandler(positive, negative, Load_P);
            processor = new Server2CN.Processor<CNHandler>(handler);
            Runnable singleCN = new Runnable() {
                public void run() {
                    singleCN(processor, port_num, Load_P);
                }
            };
            new Thread(singleCN).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void singleCN(Server2CN.Processor<CNHandler> processor, int port_num, double Load_P) {
        try {
            TServerTransport ST = new TServerSocket(port_num);
            TTransportFactory factory = new TFramedTransport.Factory();
            CNHandler handler = new CNHandler(positive, negative, Load_P);
            processor = new Server2CN.Processor<CNHandler>(handler);
            TThreadPoolServer.Args args = new TThreadPoolServer.Args(ST);
            args.processor(processor);
            args.transportFactory(factory);
            TServer server = new TThreadPoolServer(args);
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
