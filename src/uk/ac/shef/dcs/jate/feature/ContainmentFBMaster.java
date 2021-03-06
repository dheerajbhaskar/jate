package uk.ac.shef.dcs.jate.feature;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.lucene.index.*;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.util.SolrUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;

/**
 * Created by zqz on 17/09/2015.
 */
public class ContainmentFBMaster extends AbstractFeatureBuilder {

    private static final Logger LOG = Logger.getLogger(ContainmentFBMaster.class.getName());

    public ContainmentFBMaster(SolrIndexSearcher solrIndexSearcher, JATEProperties properties) {
        super(solrIndexSearcher, properties);
    }

    @Override
    public AbstractFeature build() throws JATEException {
        Containment feature = new Containment();
        try {
            Terms terms = SolrUtil.getTermVector(properties.getSolrFieldnameJATECTerms(), solrIndexSearcher);

            Map<Integer, Set<String>> numTokens2Terms = new HashMap<>();

            TermsEnum termsEnum = terms.iterator();
            while (termsEnum.next() != null) {
                String t = termsEnum.term().utf8ToString();
                if (t.length() == 0)
                    continue;
                int tokens = t.split("\\s+").length;
                Set<String> ts = numTokens2Terms.get(tokens);
                if (ts == null) {
                    ts = new HashSet<>();
                    ts.add(t);
                    numTokens2Terms.put(tokens, ts);
                } else
                    ts.add(t);
            }
            //start workers
            int cores = Runtime.getRuntime().availableProcessors();
            cores = (int) (cores * properties.getFeatureBuilderMaxCPUsage());
            cores = cores == 0 ? 1 : cores;
            Set<String> uniqueTerms = new HashSet<>();
            for (Set<String> v : numTokens2Terms.values())
                uniqueTerms.addAll(v);
            StringBuilder sb = new StringBuilder("Building features using cpu cores=");
            sb.append(cores).append(", total terms=").append(uniqueTerms.size()).append(", max per worker=")
                    .append(properties.getFeatureBuilderMaxTermsPerWorker());
            LOG.info(sb.toString());
            ContainmentFBWorker worker = new
                    ContainmentFBWorker(properties, new ArrayList<>(uniqueTerms),
                    numTokens2Terms,
                    feature, properties.getFeatureBuilderMaxTermsPerWorker());
            ForkJoinPool forkJoinPool = new ForkJoinPool(cores);
            int[] total = forkJoinPool.invoke(worker);
            sb = new StringBuilder("Complete building features. Total=");
            sb.append(total[1]).append(" success=").append(total[0]);
            LOG.info(sb.toString());

        } catch (IOException ioe) {
            StringBuilder sb = new StringBuilder("Failed to build features!");
            sb.append("\n").append(ExceptionUtils.getFullStackTrace(ioe));
            LOG.severe(sb.toString());
        }

        return feature;
    }
}
