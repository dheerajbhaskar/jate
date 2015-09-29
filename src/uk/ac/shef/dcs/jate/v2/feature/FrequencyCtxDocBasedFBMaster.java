package uk.ac.shef.dcs.jate.v2.feature;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;
import uk.ac.shef.dcs.jate.v2.JATEException;
import uk.ac.shef.dcs.jate.v2.JATEProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;

/**
 *
 */
public class FrequencyCtxDocBasedFBMaster extends AbstractFeatureBuilder {
    private static final Logger LOG = Logger.getLogger(FrequencyCtxDocBasedFBMaster.class.getName());

    protected int apply2Terms = 0; //1 means no= words

    public FrequencyCtxDocBasedFBMaster(IndexReader index, JATEProperties properties,
                                        int apply2Terms) {
        super(index, properties);
        this.apply2Terms = apply2Terms;
    }

    @Override
    public AbstractFeature build() throws JATEException {
        FrequencyCtxBased feature = new FrequencyCtxBased();
        String targetField = apply2Terms == 0 ? properties.getSolrFieldnameJATECTerms() : properties.getSolrFieldnameJATEWords();
        try {
            Fields fields = MultiFields.getFields(indexReader);
            Terms info = fields.terms(properties.getSolrFieldnameJATETermInfo());
            if (info == null)
                throw new JATEException("Cannot find expected field: " + properties.getSolrFieldnameJATETermInfo());
            Terms terms = fields.terms(targetField);
            if (terms == null)
                throw new JATEException("Cannot find expected field: " + targetField);

            List<BytesRef> allLuceneTerms = new ArrayList<>();

            TermsEnum termsEnum = terms.iterator();
            while (termsEnum.next() != null) {
                BytesRef t = termsEnum.term();
                if (t.length == 0)
                    continue;
                allLuceneTerms.add(BytesRef.deepCopyOf(t));
            }
            //start workers
            int cores = Runtime.getRuntime().availableProcessors();
            cores = (int) (cores * properties.getFeatureBuilderMaxCPUsage());
            cores = cores == 0 ? 1 : cores;
            FrequencyCtxDocBasedFBWorker worker = new
                    FrequencyCtxDocBasedFBWorker(properties, allLuceneTerms,
                    indexReader, properties.getFeatureBuilderMaxTermsPerWorker(), targetField,
                    info.iterator());
            ForkJoinPool forkJoinPool = new ForkJoinPool(cores);
            feature = forkJoinPool.invoke(worker);
            StringBuilder sb = new StringBuilder("Complete building features. Total=");
            sb.append(feature.getMapCtx2TTF().size());
            LOG.info(sb.toString());

        } catch (IOException ioe) {
            StringBuilder sb = new StringBuilder("Failed to build features!");
            sb.append("\n").append(ExceptionUtils.getFullStackTrace(ioe));
            LOG.severe(sb.toString());
        }
        return feature;
    }
}
