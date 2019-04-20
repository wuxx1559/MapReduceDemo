import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

import java.util.*;
import java.io.*;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

public class ServerHandler implements Client2Server.Iface {
    private final String[] host_add;
    private final int[] port_num;
    private static String final_file = null;
    private static List<String> mid_filenames = Collections.synchronizedList(new ArrayList<String>());
    private static AtomicInteger finished_task = new AtomicInteger(0);
    private static AtomicInteger running_task = new AtomicInteger(0);
    private static Logger log = Logger.getLogger("Server");

    public ServerHandler(String[] _host, int[] _port) {
        this.host_add = _host;
        this.port_num = _port;
    }

    @Override
    public Result job(final List<String> filenames) {
        long startTime = System.currentTimeMillis();

        //server to compute_node: map tasks
        final int cfg_tot = host_add.length;
        int num_threads = filenames.size();
        Thread[] threads_map = new Thread[num_threads];
        for (int i = 0; i < num_threads; i++) {
            String file = filenames.get(i);
            threads_map[i] = new Thread(new Runnable() {
                public void run() {
                    running_task.incrementAndGet();
                    String inter_file = null;
                    while (null == inter_file) {
                        int random_cn = ThreadLocalRandom.current().nextInt(1, cfg_tot);
                        // host_add && port_num index[1,2,3,4] is for CN.
                        inter_file = s_to_cn_map(file, host_add[random_cn], port_num[random_cn]);
                    }
                    mid_filenames.add(inter_file);
                    running_task.decrementAndGet();
                    finished_task.incrementAndGet();
                    log.info("(Map job) running tasks: " + running_task +
                            ", finished tasks: " + finished_task);
                }
            });
            threads_map[i].start();
        }

        //guarantee all map tasks end before sort task starts.
        try {
            for (int i = 0; i < num_threads; i++) {
                threads_map[i].join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //server to computer_node: sort task
        Thread thread_sort = new Thread(new Runnable() {
            public void run() {
                log.info("Sort job start...");
                int random_cn = ThreadLocalRandom.current().nextInt(1, cfg_tot);
                final_file = s_to_cn_sort(mid_filenames, host_add[random_cn], port_num[random_cn]);
                log.info("Sort job finished.");
            }
        });
        thread_sort.start();
        try {
            thread_sort.join();
        } catch (Exception e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        Result res = new Result(final_file, (int)(endTime - startTime));
        return res;
    }

    public static String s_to_cn_map(final String file_name, String host, int port) {
        String res = null;
        try {
            TTransport tran = new TSocket(host, port);
            TProtocol protocol = new TBinaryProtocol(new TFramedTransport(tran));
            Server2CN.Client c = new Server2CN.Client(protocol);
            //Try to connect
            tran.open();
            if (c.ping()) {
                res = c.MapTask(file_name);
            }
            tran.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    public static String s_to_cn_sort(final List<String> filenames, String host, int port) {
        String res = null;
        try {
            TTransport tran = new TSocket(host, port);
            TProtocol protocol = new TBinaryProtocol(new TFramedTransport(tran));
            Server2CN.Client c = new Server2CN.Client(protocol);
            //Try to connect
            tran.open();
            res = c.SortTask(filenames);
            // sort task cannot be rejected, so we don't need ping()
            tran.close();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }
}
