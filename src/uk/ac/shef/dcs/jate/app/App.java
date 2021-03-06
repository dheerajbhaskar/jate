package uk.ac.shef.dcs.jate.app;

import com.google.gson.Gson;
import org.apache.lucene.index.LeafReader;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.SolrCore;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.algorithm.TermInfoCollector;
import uk.ac.shef.dcs.jate.feature.FrequencyTermBased;
import uk.ac.shef.dcs.jate.model.JATETerm;
import uk.ac.shef.dcs.jate.util.IOUtil;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**.
 */
public abstract class App {
    protected static final double DEFAULT_THRESHOLD_N=0.25;
    protected Logger log = Logger.getLogger(this.getClass().getName());

    public abstract List<JATETerm> extract(SolrCore core, String jatePropertyFile, Map<String, String> params) throws IOException, JATEException;
    public List<JATETerm> extract(String solrHomePath, String coreName, String jatePropertyFile, Map<String, String> params) throws IOException, JATEException {
        EmbeddedSolrServer solrServer= new EmbeddedSolrServer(Paths.get(solrHomePath), coreName);
        SolrCore core = solrServer.getCoreContainer().getCore(coreName);
        List<JATETerm> result= extract(core, jatePropertyFile, params);
        solrServer.close();
        core.close();
        return result;
    }

    public void collectTermInfo(LeafReader leafReader, List<JATETerm> terms,
                                       String ngramInfoFieldname, String idFieldname) throws IOException {
        Logger log = Logger.getLogger(this.getClass().getName());
        TermInfoCollector infoCollector = new TermInfoCollector(leafReader, ngramInfoFieldname, idFieldname);

        log.info("Gathering term information (e.g., provenance and offsets). This may take a while. Total="+terms.size());
        int count=0;
        for(JATETerm jt: terms) {
            jt.setTermInfo(infoCollector.collect(jt.getString()));
            count++;
            if(count%500==0)
                log.info("done "+count);
        }
    }

    protected void filter(List<String> candidates, FrequencyTermBased fFeature, int cutoff){
        StringBuilder s = new StringBuilder("Filter candidates by cutoff frequency=");
        s.append(cutoff).append(". Before=").append(candidates.size()).append(" After=");
        Iterator<String> it = candidates.iterator();
        while(it.hasNext()){
            String t = it.next();
            if(fFeature.getTTF(t)<cutoff)
                it.remove();
        }
        s.append(candidates.size());
        log.info(s.toString());
    }

    protected static Map<String, String> getParams(String[] args) throws JATEException {
        Map<String, String> params = new HashMap<>();
        for(int i=0; i< args.length; i++){
            if(i+1<args.length) {
                String param = args[i];
                String value = args[i + 1];
                i++;
                params.put(param, value);
            }
        }
        return params;
    }

    protected static int getParamCutoffFreq(Map<String, String> params){
        String cutoff=params.get("-mttf");
        int minFreq=0;
        if(cutoff!=null){
            try{
                minFreq=Integer.valueOf(cutoff);
            }catch (NumberFormatException ne){}
        }
        return minFreq;
    }

    protected static void write(List<JATETerm> terms, String path) throws IOException {
        Gson gson = new Gson();
        if(path==null){
            System.out.println(gson.toJson(terms));
        }
        else{
            Writer w = IOUtil.getUTF8Writer(path);
            gson.toJson(terms,w);
            w.close();
        }
    }

    protected static List<JATETerm> applyThresholds(List<JATETerm> terms, String t, String n) {
        List<JATETerm> selected = new ArrayList<>();
        if(t!=null){
            try {
                double threshold = Double.valueOf(t);
                for(JATETerm jt: terms){
                    if(jt.getScore()>=threshold)
                        selected.add(jt);
                    else
                        break;
                }
            }catch(NumberFormatException nfe){}
        }

        if(n==null && selected.size()>0)
            return selected;

        if(selected.size()==0)
            selected.addAll(terms);
        double topN;
        try {
            topN=Integer.valueOf(n);
            Iterator<JATETerm> it = selected.iterator();
            int count=0;
            while(it.hasNext()){
                it.next();
                count++;
                if(count>topN)
                    it.remove();
            }
        }catch (NumberFormatException nfe){
            try{
                topN=Double.valueOf(n);
            }catch (NumberFormatException nfe2){
                topN=DEFAULT_THRESHOLD_N;
            }
            int topNInteger =(int) (topN*terms.size());
            Iterator<JATETerm> it = selected.iterator();
            int count=0;
            while(it.hasNext()){
                it.next();
                count++;
                if(count>topNInteger)
                    it.remove();
            }
        }
        return selected;
    }

    protected static void printHelp() {
        StringBuilder sb = new StringBuilder("Usage:\n");
        sb.append("java -cp '[CLASSPATH]' ").append("[package].App[Name]")
                .append(" [OPTIONS] ").append("[SOLR_HOME_PATH] [SOLR_CORE_NAME] [JATE_PROPERTY_FILE]").append("\nE.g.:\n");
        sb.append("java -cp '/libs/*b' -t 20 /solr/server/solr jate jate.properties ...\n\n");
        sb.append("[OPTIONS]:\n")
                .append("\t\t-c\t\t'true' or 'false'. Whether to collect term information, e.g., offsets in documents. Default is false.\n")
                .append("\t\t-t\t\tA number. Score threshold for selecting terms. If not set then default -n is used.").append("\n")
                .append("\t\t-n\t\tA number. If an integer is given, top N candidates are selected as terms. \n")
                .append("\t\t\t\tIf a decimal number is given, top N% of candidates are selected. Default is 0.25.\n");
        sb.append("\t\t-o\t\tA file path. If provided, the output is written to the file. \n")
                .append("\t\t\t\tOtherwise, output is written to the console.");
        System.out.println(sb);
    }
}
