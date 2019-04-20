import java.util.*;
import java.io.*;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;

public class CNHandler implements Server2CN.Iface {
    private final Set<String> positive;
    private final Set<String> negative;
    private final double Load_P;
    private static Logger log = Logger.getLogger("CN");
    private static AtomicInteger recieved_task = new AtomicInteger(0);
    private static AtomicInteger cost_time = new AtomicInteger(0);

    public CNHandler(Set<String> _pos, Set<String> _neg, double _P) {
        this.positive = _pos;
        this.negative = _neg;
        this.Load_P = _P;
    }

    @Override
    public boolean ping() {
        Random r = new Random();
        // load balancing
        if (r.nextDouble() > Load_P) {
            if (r.nextDouble() < Load_P) {
                // load injection
                try {
                    Thread.sleep(3000);
                    // sleep for 3000ms (3s)
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public String MapTask(String filename) {
        long startTime = System.currentTimeMillis();
        recieved_task.incrementAndGet();
        log.info("Recieved tasks: " + recieved_task);
        List<String> wordlist = new ArrayList<String>();
        try {
            BufferedReader input = new BufferedReader(new FileReader(filename));
            String readline = null;
            while (null != (readline = input.readLine())) {
                readline = readline.replaceAll("--", " ");
                String[] wordline = readline.split("[^a-zA-Z-]");
                // using regex to be filter
                for (String word : wordline) {
                    if (0 != word.length()){
                        wordlist.add(word.toLowerCase());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // split the input and add every words into wordlist

        HashMap<String, Integer> dict = new HashMap<String, Integer>();
        for (String word : wordlist) {
            if (null != dict.get(word)) {
                dict.put(word, dict.get(word) + 1);
            }
            else {
                dict.put(word, 1);
            }
        }
        // insert those words into a BST

        int pos = 0, neg = 0;
        for (String word : dict.keySet()) {
            if (positive.contains(word)) {
                pos += dict.get(word);
            }
            if (negative.contains(word)){
                neg += dict.get(word);
            }
        }
        double score = (double)(pos - neg)/(double)(pos + neg);
        // counting sentiment score

        try {
            File dir = new File("data/intermediate_dir/");
            if (!dir.exists()) {
                dir.mkdir();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // change dir, if not exist, make one

        String[] wordline = filename.split("/");
        String file = wordline[wordline.length - 1];
        String outfile = "data/intermediate_dir/"+file;
        try {
            FileWriter output= new FileWriter(outfile);
            String outstr = new String(filename + " " + score + "\n");
            output.write(outstr);
            output.close();
            // write the filename:sentiment to file, return outfile name
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info(filename + "\n" +
                "Positive word: " + pos + " negative word: " + neg + "\n" +
                "Sentiment score: " + score);
        long endTime = System.currentTimeMillis();
        cost_time.addAndGet((int)(endTime - startTime));
        int avg_time = cost_time.get() / recieved_task.get();
        log.info("average time: " + avg_time + "ms.");
        return outfile;
    }

    @Override
    public String SortTask(List<String> filenames) {
        long startTime = System.currentTimeMillis();
        recieved_task.incrementAndGet();
        log.info("Recieved tasks: " + recieved_task);
        HashMap<String, Double> scoremap = new HashMap<String, Double>();
        try {
            for (String file : filenames) {
                BufferedReader input = new BufferedReader(new FileReader(file));
                String readline = input.readLine();
                String[] word = readline.split(" ");
                Double score = Double.parseDouble(word[1]);
                scoremap.put(word[0], score);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // read data and then insert them into List


        List<Map.Entry<String, Double>> helplist = new ArrayList<Map.Entry<String, Double>>(scoremap.entrySet());
        Collections.sort(helplist, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> obj_1,
                    Map.Entry<String, Double> obj_2) {
                Double score_1 = obj_1.getValue();
                Double score_2 = obj_2.getValue();
                return score_2.compareTo(score_1);
            }
        });
        // sort

        String outfile = "data/output.txt";
        try {
            FileWriter output= new FileWriter(outfile);
            for (Map.Entry<String, Double> item : helplist) {
                String outstr = new String(item.getKey() + " " + item.getValue() + "\n");
                output.write(outstr);
            }
            output.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        cost_time.addAndGet((int)(endTime - startTime));
        int avg_time = cost_time.get() / recieved_task.get();
        log.info("average time: " + avg_time + "ms.");
        return outfile;
    }
}
