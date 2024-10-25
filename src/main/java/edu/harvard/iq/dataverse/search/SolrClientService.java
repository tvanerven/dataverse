package edu.harvard.iq.dataverse.search;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

import edu.harvard.iq.dataverse.settings.FeatureFlags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Singleton;
import jakarta.inject.Named;
import java.util.logging.Logger;

/**
 *
 * @author landreev
 * 
 * This singleton is dedicated to initializing the HttpSolrClient, or the Http2SolrClient
 * (if feature-flag is enabled), used by the application to talk to the search engine,
 * and serving it to all the other classes that need it.
 * This ensures that we are using one client only - as recommended by the 
 * documentation. 
 */
@Named
@Singleton
public class SolrClientService extends AbstractSolrClientService {
    private static final Logger logger = Logger.getLogger(SolrClientService.class.getCanonicalName());
    
    private SolrClient solrClient;
    
    @PostConstruct
    public void init() {
        if (FeatureFlags.ENABLE_HTTP2_SOLR_CLIENT.enabled()) {
            solrClient = new Http2SolrClient.Builder(getSolrUrl()).build();
        } else {
            solrClient = new HttpSolrClient.Builder(getSolrUrl()).build();
        }
    }
    
    @PreDestroy
    public void close() {
        close(solrClient);
    }

    public SolrClient getSolrClient() {
        // Should never happen - but? 
        if (solrClient == null) {
            init(); 
        }
        return solrClient;
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }
}
