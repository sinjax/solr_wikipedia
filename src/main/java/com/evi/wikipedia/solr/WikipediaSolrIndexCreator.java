package com.evi.wikipedia.solr;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.TrieDateField;


/**
 * @author Jonathan Hare (jsh2@ecs.soton.ac.uk), Sina Samangooei
 *         (ss@ecs.soton.ac.uk), David Duplaw (dpd@ecs.soton.ac.uk)
 * 
 */
public class WikipediaSolrIndexCreator implements Closeable {
	/** Logging */
	private static Logger log = Logger.getLogger(WikipediaSolrIndexCreator.class);

	/** Some constant counters */
	private static int BATCH_SIZE = 20000;

	/** Solr file names */
	private static String SOLR_CONFIG = "solrconfig.xml";
	private static String SOLR_SCHEMA = "schema.xml";

	interface WikiDocumentAppender {
		public void addFieldToDoc(WikiPage p, SolrInputDocument doc);
	}

	private ArrayList<WikiDocumentAppender> columns;

	private void prepareAppenders() {
		columns = new ArrayList<WikiDocumentAppender>();
		columns.add(new WikiDocumentAppender() {
			@Override
			public void addFieldToDoc(WikiPage p, SolrInputDocument doc) {
				doc.addField("id", p.getId());
			}
		});
	}

	/** Solr index */
	private SolrCore solrCore;
	private CoreContainer solrContainer;
	private EmbeddedSolrServer solrServer;

	private WikipediaPageParser wikiStream;

	private Map<String, Integer> csv;

	/**
	 * Basic constructor. Instantiate our reader and Solr.
	 * 
	 * @param sourceFile
	 *            The input file to read
	 * @throws Exception
	 *             if any errors occur
	 */
	public WikipediaSolrIndexCreator(File sourceFile) throws Exception {

		this.wikiStream = new WikipediaPageParser(sourceFile);
		this.wikiStream.openInputStream();

		// Time to bring Solr online
		// Find the Solr home
		String solrHome = System.getProperty("solr.home");
		if (solrHome == null) {
			throw new Exception("No 'solr.home' provided!");
		}
		File solrHomeFile = new File(solrHome);
		if (!solrHomeFile.exists()) {
			solrHomeFile.mkdirs();
			File confDir = new File(solrHomeFile, "conf");
			confDir.mkdirs();
			FileUtils.copyInputStreamToFile(WikipediaSolrIndexCreator.class
					.getResourceAsStream(SOLR_CONFIG), new File(confDir,
					SOLR_CONFIG));
			FileUtils.copyInputStreamToFile(WikipediaSolrIndexCreator.class
					.getResourceAsStream(SOLR_SCHEMA), new File(confDir,
					SOLR_SCHEMA));
		}
		solrServer = startSolr(solrHome);
		prepareAppenders();
	}

	private WikipediaSolrIndexCreator() {
		prepareAppenders();
	}

	/**
	 * Start up an embedded Solr server.
	 * 
	 * @param home
	 *            The path to the Solr home directory
	 * @return EmbeddedSolrServer: The instantiated server
	 * @throws Exception
	 *             if any errors occur
	 */
	private EmbeddedSolrServer startSolr(String home) throws Exception {
		try {
			SolrConfig solrConfig = new SolrConfig(home, SOLR_CONFIG, null);
			IndexSchema schema = new IndexSchema(solrConfig, SOLR_SCHEMA, null);

			solrContainer = new CoreContainer(new SolrResourceLoader(
					SolrResourceLoader.locateSolrHome()));
			CoreDescriptor descriptor = new CoreDescriptor(solrContainer, "",
					solrConfig.getResourceLoader().getInstanceDir());
			descriptor.getPersistableUserProperties().setProperty(
					CoreDescriptor.CORE_CONFIG, solrConfig.getResourceName());
			descriptor.getPersistableUserProperties().setProperty(
					CoreDescriptor.CORE_SCHEMA, schema.getResourceName());

			solrCore = new SolrCore(null, solrConfig.getDataDir(), solrConfig,
					schema, descriptor);
			solrContainer.register("cheese", solrCore, false);
			// CoreAdminRequest.create
			return new EmbeddedSolrServer(solrContainer, "cheese");
		} catch (Exception ex) {
			log.error("\nFailed to start Solr server\n");
			throw ex;
		}
	}

	/**
	 * Force a commit against the underlying Solr database.
	 * 
	 */
	private void commit() {
		try {
			solrServer.commit();
		} catch (Exception ex) {
			log.error("Failed to commit: ", ex);
		}
	}

	/**
	 * Force an optimize call against the underlying Solr database.
	 * 
	 */
	private void optimize() {
		try {
			solrServer.optimize();
		} catch (Exception ex) {
			log.error("Failed to commit: ", ex);
		}
	}

	/**
	 * Main processing loop for the function
	 * 
	 * @param counter
	 *            The number of rows to execute during this loop
	 * @return int: The number of rows read this pass
	 * @throws Exception
	 *             if any errors occur
	 */
	public int loop(final int counter) throws Exception {
		int seen = 0;
		for (int i = 0; i < counter; i++) {
			WikipediaSolrIndexCreator.this.process(this.wikiStream.getNextWikiPage());
		}

		return seen;
	}

	/**
	 * Process the photo
	 * 
	 * @param p
	 *            photo
	 */
	private void process(WikiPage p) {
		try {
			solrServer.add(createSolrDoc(p, this));
		} catch (Exception ex) {
			log.error("Failed to add document:");
			log.error("Stack trace: ", ex);
		}
	}

	/**
	 * Create a Solr document from the provided Geonames column data.
	 * 
	 * @param p
	 *            the photo object
	 * 
	 * @return SolrInputDocument: The prepared document
	 */
	public static SolrInputDocument createSolrDoc(WikiPage p) {
		return createSolrDoc(p, new WikipediaSolrIndexCreator());
	}

	/**
	 * Create a Solr document from the provided Geonames column data.
	 * 
	 * @param p
	 *            the photo object
	 * 
	 * @return SolrInputDocument: The prepared document
	 */
	public static SolrInputDocument createSolrDoc(WikiPage p,
			WikipediaSolrIndexCreator ret) {

		SolrInputDocument doc = new SolrInputDocument();
		for (WikiDocumentAppender key : ret.columns) {
			key.addFieldToDoc(p, doc);
		}
		return doc;
	}

	/**
	 * Command line entry point.
	 * 
	 * @param args
	 *            Array of String parameters from the command line
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		// Make we were given an appropriate parameter
		if (args.length < 1) {
			log.error("ERROR: Usage requires xml file!");
			return;
		}

		// Validate it
		File file = new File(args[0]);
		if (file == null || !file.exists()) {
			log.error("ERROR: The input file does not exist!");
			return;
		}

		// Get ready to harvest
		
		try(WikipediaSolrIndexCreator harvester = new WikipediaSolrIndexCreator(file);){
			log.debug("\n\n===================\n\n");

			int count = 0;

			// Run a single batch
			try {
				while (true) {
					int read = harvester.loop(BATCH_SIZE);

					count += read;
					log.info("Rows read: " + count);

					// Commit after each batch
					try {
						harvester.commit();
					} catch (Exception ex) {
						log.info("Commit failed");
						log.error("Stack trace: ", ex);
					}

					// Did we finish?
					if (read < BATCH_SIZE) {
						break;
					}
				}
			} catch (Exception ex) {
				log.error("ERROR: An error occurred in the processing loop: ", ex);
			}

			try {
				harvester.commit();
				log.info("Index optimize...");
				harvester.optimize();
				log.info("... completed");
			} catch (Exception ex) {
				log.info("... failed");
				log.error("Stack trace: ", ex);
			}
			log.info("\n\n===================\n\n");
		} catch (Exception ex) {
			// A reason for death was logged in the constructor
			log.error("Stack trace: ", ex);
		}

		
	}

	@Override
	public void close() throws IOException {
		this.wikiStream.close();
		if (solrContainer != null) {
			solrContainer.shutdown();
		}
	}
}
