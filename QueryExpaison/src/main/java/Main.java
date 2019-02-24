package main.java;

import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.Map;

public class Main {

    private static String INDEX_DIRECTORY;
    public static void main(String[] args) throws IOException, ParseException {

        String queryPath = "";
        String indexPath = "";

        INDEX_DIRECTORY = args[0];
        queryPath = args[1];
        //dataPath = args[2];

        QueryData queryData = new QueryData(queryPath);
        //
        Map<String,String> pageMap = queryData.getAllPageQueries();
        Map<String,String> sectionMap = queryData.getAllSectionQueries();


        QueryExpansion qe = new QueryExpansion(pageMap,indexPath);

        qe.run();

    }

}
