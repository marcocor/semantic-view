package it.unipi.di.acube.semanticview.servlet;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextListener implements ServletContextListener {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public final static String STORAGE_DIR_PARAMETER = "it.unipi.di.acube.semanticview.storageDirectory";
	public final static String UPLOAD_DIR_PARAMETER = "it.unipi.di.acube.semanticview.uploadDirectory";
	public final static String STORAGE_PATH_PARAMETER = "it.unipi.di.acube.semanticview.storagePath";

	@Override
	public void contextInitialized(ServletContextEvent e) {
		LOG.info("Creating Semantic View context.");
		ServletContext context = e.getServletContext();
		String storageDirectory = context.getInitParameter(STORAGE_DIR_PARAMETER);
		String storagePath = context.getInitParameter(STORAGE_PATH_PARAMETER);
		String uploadDirectory = context.getInitParameter(UPLOAD_DIR_PARAMETER);
		try {
			Storage s = new Storage(storageDirectory, storagePath, uploadDirectory);
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
