# Design Document

## 0. Requirement

* Five machines (one for Client and Server, four for Compute Nodes)	
* thrift 0.9.3 (CSE lab's version)
* vim, Linux command

## 1. Interface

This part is written in thrift, and then we can use the command line below to generate `.java` files.

```bash
thrift -r --gen java mapreduce.thrift
```

### 1.1. Client to Server

The communication between the Client and the Server should be like this:

> Client: send the filenames that need processing
>
> Server: record the total running time, and return the out_file (contains sorted filenames ordered by sentiment score)

Therefore, we should first design a struct that can hold the return value from the Server, And name it `Result`. And then we call the service Client2Server, since it is the connection between Client to Server.

```thrift
struct Result {
    1: string filename;
    2: double elapsed_time;
}
service Client2Server {
    Result job(1: list<string> filenames);
}
```

### 1.2. Server to Compute Node

The Server will connect the Compute Nodes in two patterns: map and sort. Also, we should test whether the connection to the server is allowed, so we also implement a ping function.

**map**

> Server: send the filename that needs counting words and computing sentiment score
>
> Compute Node: return the name of mid_file which contains the name of the file receiveed and corresponding sentiment score

**sort**

> Server: send all names of mid_file that needs to be sorted by the order of sentiment score
>
> Compute Node: return the out_file, which contains ordered list of <filename, score>

**ping**

> Client-Server: NULL
>
> Server-Compute Node: return true if the connection is allowed, return false if the connection is refused

```thrift
service Server2CN {
    bool ping();
    string MapTask(1: string filename);
    string SortTask(1: list<string> filename);
}
```

## 2. Machine (or terminal) parts

### 2.1. Client

The structure of Client is extremely simple: read the config file of server, send request to the server, and wait for response. It will use the interface given by thrift. Finally, it will print total elapsed time and the content of the output file.

```Java
res = Result job(List<String> filenames);
```

Client needs to use following function to get all names of file needing to be processed in the input directory. We use a class from the I/O library to solve it like below:

```Java
List<String> filenames  = new ArrayList<String>();
File[] files = new File(input_dir).listFiles();
for (File file: files) {
    if (file.isFile()) {
        filenames.add(input_dir + file.getName());
    }
}
```

### 2.2. Server

In the file `Server.java`, we only apply a `TTSimpleServer`. If will read the config files of itself and Compute Nodes. At the beginning and the end of the Server, it will call `System.currentTimeMillis()` to get the current time and compute `endTime - startTime` to get time consumption of total, which is the elapsed time needed.

```Java
long startTime = System.currentTimeMillis();
long endTime = System.currentTimeMillis();
```

In the source code `ServerHandler.java`, it can do two tasks as:

```Java
mid_file = String MapTask(String filename);
out_file = String SortTask(List<String> filenames);
```

For the reason that the Compute Nodes are multiple threads servers, We create a single thread for each task and connect each of them to each Compute Node by random. For running the threads, we use `start()` function, and to wait for all the map tasks end, we use `join()` function. Below is the part of map tasks, and for sort task, we use similar approach.

```Java
for (int i = 0; i < num_threads; i++) {
    String file = filenames.get(i);
    threads_map[i] = new Thread(new Runnable() {
        public void run() {
            running_task.incrementAndGet();
            String inter_file = null;
            while (null == inter_file) {
                int random_cn = ThreadLocalRandom.current().nextInt(1, host_add.length);
                // host_add && port_num index[1,2,3,4,...] is for CN.
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
```

And we found that ArrayList<> and int are not thread-safe type. So for variable mid_filenames, we use `Collections.synchronizedList(new ArrayList<String>())` and for some integer value in the thread, we use `AtomicInteger`.

### 2.3. Computing Node

The Compute Node is a multiple threads server `TThreadPoolServer`.

When there is a request (i.e mapTask or sortTask) from the Server to Compute Node, the Compute Node will produce a thread to handle it. However, for map task, if every thread reads the `positive.txt` and `negative.txt`, it will be costly. So, in the main thread of the Compute Node, it will read the words in `positive.txt` and `negative.txt`, and then add them into two `HashSet<String>`. When a handler is established, the main thread will pass the two word dictionaries as parameters. Therefore, all threads produced for each task in this Compute Node could have access to that.

#### 2.3.1. ping

When the Server want to connect Compute Nodes to execute Mapatsk, it should use `ping()` try to test whether Compute Node will accept or reject.	

In our function, it first test whether accept or reject with the Load Possibility P.	

If the Compute Node decides to accept, it will use a random number to make a decision whether inject the task soon or inject a delay.	

```Java	
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
```

#### 2.3.2. MapTask

For the map task, first we read every line of the file, and then replace "--" with space " ". Next, we split it with regex "[^a-zA-Z-]" to get all words. Then we insert those words into a dictionary to count the appear time of each word. Finally, we check the `positive.txt` and `negative.txt` to get the number of words for positive type and negative type and count the sentiment score.

```Java
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
```

#### 2.3.3. SortTask

The sort task is easier than map task. We read all files and insert the <filename, score> into a `HashMap<String, Double>`. Next we sort it in descending order with the helplist of the `HashMap<String, Double>` and self-defined compare function.

```Java
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
```

## 3. Useful scripts

### 3.1 `Makefile`

The `Makefile` gives us a easy way to compile the program and clean all the files produced. For the compile part, we can use `make` command to compile Client, Server, and CN (Compute Node). For cleaning part, using `make clean` can remove all `.class` files.

```Makefile
all : Client Server CN
.PHONY : all
Client:
	javac -cp ".:/usr/local/Thrift/*" Client.java -d .
Server:
	javac -cp ".:/usr/local/Thrift/*" Server.java -d .
CN:
	javac -cp ".:/usr/local/Thrift/*" CN.java -d .

.PHONY : clean
clean:
	rm -rf *.class
```

### 3.2 `test.sh`

```bash
if [ $# -eq 1 ]
then
    if [ $1 == 'Client' ]
    then
        java -cp ".:/usr/local/Thrift/*" Client data/input_dir/ config/server.cfg
    elif [ $1 == 'Server' ]
    then
        java -cp ".:/usr/local/Thrift/*" Server config/server.cfg config/CN1.cfg config/CN2.cfg config/CN3.cfg config/CN4.cfg
    else
        java -cp ".:/usr/local/Thrift/*" CN config/$1.cfg
    fi
else
    echo "Parameter Error. Using \"./test.sh Client\" , \"./test.sh Server\" or \"./test.sh CN[#]\"" 
fi
```

The script is shown above. First the script should make sure there is only one parameter. Then run the Client, Server or Compute Node. For the Client, it need to get the IP address and port number of the server, so its parameters are the input directory (which contains all of files need processing) and the confg file of server. For the Server, it needs the configuration of itself and computer nodes (In our script there are four Compute Nodes. If you want more, just add more CN*.cfg files in the /config directory and add those name as parameters in this test.sh file). For each Compute Node, it only require the configuration file of itself.

## 4. Configuration

* `server.cfg` contains two lines: the IP address and the port number.
* `CN[#].cfg` ([#] part replaced by number) contains three lines: the IP address, the port number, and the Load Possibility.
* the usage descriptions are in `3.2`, `2.2` and `2.3`

## 5. Other Information

We implement two scheduling policy as `random` and `load-balancing` when assigning each task to each Compute Node. It is sent to each Computer Node by random, and when the task is trying to connect, each Compute Node may reject the task depending to the load-probability. For example. 0.8 load-probability means the task would be rejected with 80% chance. Other than that, if the task is lucky enough to be accepted, we implement another `load injecting` method which is used to inject delay based on the load-probability. For example. the 0.8 load-probability means after accepting the task, the task has 80% chance to inject deley i.e. 1 second.
