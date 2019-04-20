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
