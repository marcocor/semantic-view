package it.unipi.di.acube.semanticview.server;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;

public class ServerMain {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String JERSEY_SERVLET_CONTEXT_PATH = "";

	/**
	 * Starts Grizzly HTTP server exposing Semantic View JAX-RS resources.
	 * 
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void startServer(String serverUri, String documentsDir, String entitiesDir, String storagePath)
	        throws FileNotFoundException, IOException {
		LOG.info("Initializing Semantic View services.");
        WebappContext context = new WebappContext("WebappContext", JERSEY_SERVLET_CONTEXT_PATH);
        context.setInitParameter(ContextListener.DOCUMENT_DIR_PARAMETER, documentsDir);
        context.setInitParameter(ContextListener.ENTITIES_DIR_PARAMETER, entitiesDir);
        context.setInitParameter(ContextListener.STORAGE_PATH_PARAMETER, storagePath);

		ResourceConfig rc = new ResourceConfig().packages("it.unipi.di.acube.semanticview.server.rest");
		rc.property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
		StaticHttpHandler staticHandler = new StaticHttpHandler("src/main/webapp/");

		HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(serverUri), rc);
		httpServer.getServerConfiguration().addHttpHandler(staticHandler, "/");
        context.deploy(httpServer);

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Shutting server down..");
				httpServer.shutdown();
			}
		}, "shutdownHook"));

		try {
			httpServer.start();
			LOG.info("Semantic View started with WADL available at " + "{}application.wadl\nPress CTRL^C (SIGINT) to terminate.",
			        serverUri);
			Thread.currentThread().join();
		} catch (Exception e) {
			LOG.error("There was an error while starting Grizzly HTTP server.", e);
		}
	}

	/**
	 * Main method.
	 * 
	 * @param args
	 * @throws IOException
	 * @throws ParseException
	 */
	public static void main(String[] args) throws IOException, ParseException {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption(Option.builder("h").required().hasArg().argName("HOSTNAME").desc("Server hostname").longOpt("host").build());
		options.addOption(Option.builder("p").required().hasArg().argName("PORT").desc("TCP port to listen.").longOpt("port").build());
		options.addOption(Option.builder("d").required().hasArg().argName("DOC_PATH").desc("Path to directory containing document CSVs.").longOpt("documents-dir").build());
		options.addOption(Option.builder("e").required().hasArg().argName("ENTITIES_PATH").desc("Path to directory containing entities CSVs.").longOpt("entities-dir").build());
		options.addOption(Option.builder("s").required().hasArg().argName("STORAGE_PATH").desc("Path to store Semantic View internal data.").longOpt("storage-path").build());
		CommandLine line = parser.parse(options, args);

		String serverUri = String.format("http://%s:%d/rest", line.getOptionValue("host", "localhost"),
		        Integer.parseInt(line.getOptionValue("port", "8080")));
		startServer(serverUri, line.getOptionValue("documents-dir"), line.getOptionValue("entities-dir"), line.getOptionValue("storage-path"));
	}
}
