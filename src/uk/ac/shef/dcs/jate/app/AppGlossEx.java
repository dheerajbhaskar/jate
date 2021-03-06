package uk.ac.shef.dcs.jate.app;

import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.algorithm.GlossEx;
import uk.ac.shef.dcs.jate.feature.*;
import uk.ac.shef.dcs.jate.model.JATETerm;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 */
public class AppGlossEx extends App {
    public static void main(String[] args) throws JATEException, IOException {
        if (args.length < 1) {
            printHelp();
            System.exit(1);
        }
        String solrHomePath = args[args.length - 3];
        String solrCoreName=args[args.length-2];
        String jatePropertyFile=args[args.length - 1];

        Map<String, String> params = getParams(args);

        List<JATETerm> terms = new AppGlossEx().extract(solrHomePath, solrCoreName, jatePropertyFile, params);
        String paramValue=params.get("-o");
        write(terms,paramValue);
    }

    @Override
    public List<JATETerm> extract(SolrCore core, String jatePropertyFile, Map<String, String> params) throws IOException, JATEException {
        SolrIndexSearcher searcher = core.getSearcher().get();

        JATEProperties properties = new JATEProperties(jatePropertyFile);
        FrequencyTermBasedFBMaster ftbb = new
                FrequencyTermBasedFBMaster(searcher, properties, 0);
        FrequencyTermBased ftb = (FrequencyTermBased)ftbb.build();

        FrequencyTermBasedFBMaster fwbb = new
                FrequencyTermBasedFBMaster(searcher, properties, 1);
        FrequencyTermBased fwb = (FrequencyTermBased)fwbb.build();

        TTFReferenceFeatureFileBuilder ftrb = new
                TTFReferenceFeatureFileBuilder(params.get("-r"));
        FrequencyTermBased frb = ftrb.build();

        GlossEx glossex = new GlossEx();
        glossex.registerFeature(FrequencyTermBased.class.getName(), ftb);
        glossex.registerFeature(FrequencyTermBased.class.getName()+GlossEx.SUFFIX_WORD, fwb);
        glossex.registerFeature(FrequencyTermBased.class.getName()+GlossEx.SUFFIX_REF, frb);

        List<String> candidates = new ArrayList<>(ftb.getMapTerm2TTF().keySet());
        int cutoffFreq = getParamCutoffFreq(params);
        filter(candidates, ftb, cutoffFreq);

        List<JATETerm> terms=glossex.execute(candidates);
        terms=applyThresholds(terms, params.get("-t"), params.get("-n"));
        String paramValue=params.get("-c");
        if(paramValue!=null &&paramValue.equalsIgnoreCase("true")) {
            collectTermInfo(searcher.getLeafReader(), terms, properties.getSolrFieldnameJATENGramInfo(),
                    properties.getSolrFieldnameID());
        }
        searcher.close();
        core.close();
        return terms;
    }


    protected static void printHelp() {
        StringBuilder sb = new StringBuilder("GlossEx Usage:\n");
        sb.append("java -cp '[CLASSPATH]' ").append(AppATTF.class.getName())
                .append(" [OPTIONS] ").append("-r [REF_TERM_TF_FILE] [LUCENE_INDEX_PATH] [JATE_PROPERTY_FILE]").append("\nE.g.:\n");
        sb.append("java -cp '/libs/*' -t 20 -r /resource/bnc_unifrqs.normal /solr/server/solr/jate/data jate.properties ...\n\n");
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
