package it.unipi.di.acube.semanticview.importers;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraImporter {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void importFile(File uploadedFile, String annotateJiraPythonScript, File storageDir, String gcubeToken,
	        String lang) throws IOException, InterruptedException {
		// TODO: OMG this is ugly.
		String cmd = String.format("python %s --infiles %s --outdir %s --gcube-token %s --lang %s", annotateJiraPythonScript,
		        uploadedFile.getAbsolutePath(), storageDir.getAbsolutePath(), gcubeToken, lang);
		LOG.info("Executing {}", cmd);
		Process pr = Runtime.getRuntime().exec(cmd);
		pr.waitFor();
	}

}
