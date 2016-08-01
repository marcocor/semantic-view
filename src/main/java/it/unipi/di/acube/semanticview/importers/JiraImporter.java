package it.unipi.di.acube.semanticview.importers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraImporter {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void importFile(File uploadedFile, String annotateJiraPythonScript, File storageDir, String gcubeToken,
	        String lang) throws IOException, InterruptedException {
		// TODO: OMG this is ugly.
		String cmd = "/usr/bin/python";

		ProcessBuilder pb = new ProcessBuilder(cmd, annotateJiraPythonScript, "--infiles", uploadedFile.getAbsolutePath(),
		        "--outdir", storageDir.getAbsolutePath(), "--gcube-token", gcubeToken, "--lang", lang);
		LOG.info("Executing {}", String.join(" ", pb.command()));
		pb.redirectErrorStream(true);
		Process process = pb.start();

		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null)
			LOG.info(line);

		process.waitFor();
		LOG.info("Python process done for file {}", uploadedFile.getAbsolutePath());
	}

}
