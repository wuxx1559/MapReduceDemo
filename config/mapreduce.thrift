struct Result {
    1: string filename;
    2: double elapsed_time;
}

service Client2Server {
    Result job(1: list<string> filenames);
}

service Server2CN {
    bool ping();
    string MapTask(1: string filename);
    string SortTask(1: list<string> filename);
}
