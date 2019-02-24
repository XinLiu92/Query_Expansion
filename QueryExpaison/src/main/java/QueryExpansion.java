package main.java;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.en.EnglishAnalyzer;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryExpansion {



    private IndexSearcher searcher;
    private Map<String,String> pageMap;
    private String INDEX_DIR;
    private int max_result;
    private QueryParser parser;
    static final private String OUTPUT_DIR = "output";
    public QueryExpansion(Map<String, String> pageMap, String indexPath){
        this.pageMap = pageMap;
        this.INDEX_DIR = indexPath;
        this.max_result = 100;

    }



    public void run() throws IOException, ParseException {

        System.out.println(INDEX_DIR);
        searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIR).toPath()))));

        searcher.setSimilarity(new BM25Similarity());

        parser = new QueryParser("content", new EnglishAnalyzer());
        ArrayList<String> runFileStr = new ArrayList<String>();
        for (Map.Entry<String, String> entry:pageMap.entrySet()){
            String queryStr = entry.getValue();
            String queryId = entry.getKey();
            Query q = parser.parse(QueryParser.escape(queryStr));
            TopDocs tops = searcher.search(q, max_result);
            ScoreDoc[] scoreDoc = tops.scoreDocs;

            Query newQuery = expandQueryByRocchio(queryStr,scoreDoc);

            tops = searcher.search(newQuery,max_result);


            scoreDoc = tops.scoreDocs;
            for (int i = 0; i < scoreDoc.length; i++) {
                ScoreDoc score = scoreDoc[i];
                Document doc = searcher.doc(score.doc);
                String paraId = doc.getField("paraid").stringValue();
                float rankScore = score.score;
                int rank = i + 1;

                // String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "+ rankScore + " BM25";

                String runStr = queryId+" Q0 "+paraId+" "+rank+ " "+rankScore+" "+"team3"+" QueryExpansion";
                System.out.println(runStr);
                runFileStr.add(runStr);
            }

        }

        writeToFile("page-QueryExpansion.run",runFileStr);

    }

    private static void writeToFile(String filename, ArrayList<String> runfileStrings) {
        String fullpath = OUTPUT_DIR + "/" + filename;

        try (FileWriter runfile = new FileWriter(new File(fullpath))) {
            for (String line : runfileStrings) {
                runfile.write(line + "\n");
            }
        } catch (IOException e) {
            System.out.println("Could not open " + fullpath);
        }

        System.out.println("wrote file to "+ OUTPUT_DIR);
    }

    public Query expandQueryByRocchio(String queryString, ScoreDoc[] scoreDoc) throws IOException, ParseException {
        List<Document> vhits = getDocs(queryString,scoreDoc);

        float alpha = (float) 1.0;
        float beta = (float) 0.75;
        float decay = (float) 0.04;
        int docNum = 10, termNum = 10;

        List<QueryTerms> docsTermVector = getDocsTerms(vhits,docNum,new EnglishAnalyzer());

        Query expandedQuery = adjust(docsTermVector, queryString,alpha,beta,decay,docNum,termNum);

        return expandedQuery;
    }


    public Query adjust(List<QueryTerms> docsTermVector, String queryString, float alpha, float beta, float decay, int docsRelevantCount, int maxExpandedQueryTerms) throws IOException, ParseException {

        Query expandedQuery;


        //set boost for relevent docuement
        Map<String,Float> docsTerms = setBoost(docsTermVector, beta, decay);

        //set boost for query string

        QueryTerms originalQueryTerm = new QueryTerms(queryString,new EnglishAnalyzer(),1);

        Map<String,Float> originalQuery = setBoost(originalQueryTerm,alpha);


        //combine original query and expanded query
        Map<String,Float> combinedQuery = combine(originalQuery,docsTerms);

        expandedQuery = merge(combinedQuery,10);

        return expandedQuery;

    }

    public Query merge (Map<String, Float> combinedQuery,int num) throws ParseException {
        Query query = null;

        int count = Math.min(combinedQuery.size(), num);

        StringBuffer stringBuffer = new StringBuffer();

        for (Map.Entry<String,Float> entry : combinedQuery.entrySet()){
            if (count <= 0){
                break;
            }
            count--;
            String str = entry.getKey();
            float weight = entry.getValue();
            stringBuffer.append(QueryParser.escape(str).toLowerCase()+"^"+weight+" ");

        }

        String newQueryStr = stringBuffer.toString();

        query = parser.parse(QueryParser.escape(newQueryStr));

        return query;


    }

    public Map<String, Float> combine(Map<String, Float> originalQuery, Map<String, Float> expanededQuery){

        for (Map.Entry<String,Float> entry : originalQuery.entrySet()){
            String term = entry.getKey();
            float weight = entry.getValue();
            if (expanededQuery.containsKey(term)){
                expanededQuery.put(term,expanededQuery.get(term)+weight);
            }
            else{
                expanededQuery.put(term,weight);
            }
        }

        return expanededQuery;


    }

    public Query find(Query q, List<Query> res){
        if (q == null || res.size() == 0 || res== null) return  null;
        Query found = null;
        for (Query entry : res){

            if (entry.toString("content").equals(q.toString("content"))){
                found = entry;
            }
        }

        return  found;
    }



    public Map<String, Float> setBoost(QueryTerms originalQueryTerm, float alpha){
        List<QueryTerms> list = new ArrayList<>();
        list.add(originalQueryTerm);

        return setBoost(list,alpha,0);
    }

    public Map<String,Integer> getIDF (List<QueryTerms> docsTermVector){
        Map<String,Integer>  res = new HashMap<>();
        for (QueryTerms qt : docsTermVector){
            Map<String, Integer> qtMap = qt.getTermsMap();

            for (Map.Entry<String,Integer> entry : qtMap.entrySet()){
                String term =  entry.getKey();

                if (res.containsKey(term)){
                    res.put(term,res.get(term)+1);
                }
            }
        }

        return res;
    }

    public Map<String, Float> setBoost(List<QueryTerms> docsTermVector, float factor, float decayFactor){
        List<Query> terms = new ArrayList<>();

        int totalDocument = docsTermVector.size();
        Map<String,Integer> idfMap = getIDF(docsTermVector);

        Map<String, Float> countMap = new HashMap<>();

        //set boost for each of the terms of each docs
        for (int g =0 ; g < docsTermVector.size() ; g++ ) {
            QueryTerms docTerms = docsTermVector.get(g);


            Map<String,Integer> termsMap = docTerms.getTermsMap();
            //increase decay
            float decay = decayFactor*g;

            for (Map.Entry<String,Integer> entry : termsMap.entrySet()){
                String termTxt = entry.getKey();
                int freq = entry.getValue();



                //calculate weight
                float tf = freq;

                float idf = (float) totalDocument/idfMap.get(termTxt);

                float weight = tf*idf;

                if (countMap.containsKey(termTxt)){
                    countMap.put(termTxt,countMap.get(termTxt)+weight);
                }else{
                    countMap.put(termTxt,weight);
                }

            }
        }

//        for (Map.Entry<String,Float> entry: countMap.entrySet()){
//            String str = entry.getKey();
//            float weight =  entry.getValue();
//            Term term = new Term("content",str);
//            Query termQuery = new TermQuery(term);
//            Query boostedTermQuery = new BoostQuery(termQuery,factor*weight);
//            terms.add(boostedTermQuery);
//        }

        return countMap;

    }

    public List<Document> getDocs(String queryString, ScoreDoc[] scoreDoc) throws IOException {
        List<Document> res = new ArrayList<>();

        for (int i =0;i< scoreDoc.length ;i++ ) {
            ScoreDoc score = scoreDoc[i];
            Document doc = searcher.doc(score.doc);
            res.add(doc);
        }

        return res;
    }

    public List<QueryTerms> getDocsTerms(List<Document> vhits, int docsRelevantCount, Analyzer analyzer) throws IOException {
        List<QueryTerms> res = new ArrayList<>();


        int min = Math.min(docsRelevantCount,vhits.size());
        for (int i = 0; i< min; i++) {
            Document doc = vhits.get(i);

            StringBuffer stringbuffer = new StringBuffer();
            String[] docString = doc.getValues("content");

            if (docString.length == 0) continue;

            for(int j = 0; j < docString.length;j++){
                stringbuffer.append(docString[j]+" ");
            }


            QueryTerms docTerms = new QueryTerms(stringbuffer.toString(),analyzer,1);
            res.add(docTerms);
        }

        return res;
    }
}
