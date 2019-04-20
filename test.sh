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
