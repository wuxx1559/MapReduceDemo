import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

import java.util.*;
import java.io.*;

public class Client {
    public static void main(String[] args) {
        // assume the command is: Jave Client input_dir cfg_file
        // host_name: local host --- test 1st
        int argc = args.length;
        if (argc < 2) {
            System.out.println("Wrong number of arguments!");
            return;
        }

        String input_dir = args[0];
        String cfg_file = args[1];
        String host_add = null;
        int port_num = -1;
        try {
            BufferedReader input = new BufferedReader(new FileReader(cfg_file));
            host_add = input.readLine();
            port_num = Integer.parseInt(input.readLine());
            input.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<String> filenames  = new ArrayList<String>();
        File[] files = new File(input_dir).listFiles();
        for (File file: files) {
            if (file.isFile()) {
                filenames.add(input_dir + file.getName());
            }
        }

        //Create client connect.
        try {
            TTransport transport = new TSocket(host_add, port_num);
            // host_add and port_num of the server
            TProtocol protocol = new TBinaryProtocol(new TFramedTransport(transport));
            Client2Server.Client client = new Client2Server.Client(protocol);
            //Try to connect
            transport.open();
            Result res = client.job(filenames);
            // print to terminal
            System.out.println("Total elapsed time is " + res.elapsed_time + " ms.");
            System.out.println("Ouput file: " + res.filename);
            System.out.println();
            BufferedReader input = new BufferedReader(new FileReader(res.filename));
            String word = null;
            while (null != (word = input.readLine())) {
                System.out.println(word);
            }
            input.close();
            transport.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
