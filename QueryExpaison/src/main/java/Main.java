package main.java;

import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.Map;

public class Main {

    private static String INDEX_DIRECTORY;
    public static void main(String[] args) throws IOException, ParseException {
        System.setProperty("file.encoding", "UTF-8");
        System.out.println("Start running Main");
        String queryPath = args[1];
        String indexPath = args[0];
        System.out.println("index path: "+indexPath);
        //dataPath = args[2];

        QueryData queryData = new QueryData(queryPath);
        //
        Map<String,String> pageMap = queryData.getAllPageQueries();
        Map<String,String> sectionMap = queryData.getAllSectionQueries();

        System.out.println("calling query expansion");
        QueryExpansion qe = new QueryExpansion(pageMap,sectionMap,indexPath);

        qe.runPage();
        qe.runSection();

    }

}
