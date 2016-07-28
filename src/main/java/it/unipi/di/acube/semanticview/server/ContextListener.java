package it.unipi.di.acube.semanticview.server;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextListener implements ServletContextListener {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public final static String DOCUMENT_DIR_PARAMETER = "it.unipi.di.acube.semanticview.documentDirectory";
	public final static String ENTITIES_DIR_PARAMETER = "it.unipi.di.acube.semanticview.entityDirectory";
	public final static String STORAGE_PATH_PARAMETER = "it.unipi.di.acube.semanticview.storagePath";

	@Override
	public void contextInitialized(ServletContextEvent e) {
		LOG.info("Creating Semantic View context.");
		ServletContext context = e.getServletContext();
		String documentDirectory = context.getInitParameter(DOCUMENT_DIR_PARAMETER);
		String entityDirectory = context.getInitParameter(ENTITIES_DIR_PARAMETER);
		String storagePath = context.getInitParameter(STORAGE_PATH_PARAMETER);
		try {
			Storage s = new Storage(documentDirectory, entityDirectory, storagePath);
			e.getServletContext().setAttribute("storage", s);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		LOG.info("Desttroying Semantic View context.");
		((Storage) arg0.getServletContext().getAttribute("storage")).shutdown();
	}
}
