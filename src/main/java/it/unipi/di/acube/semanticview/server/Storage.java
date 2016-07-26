package it.unipi.di.acube.semanticview.server;

import java.io.File;
import java.lang.invoke.MethodHandles;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.IndexTreeList;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Storage {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private DB db;
	
	public IndexTreeList<String> ignoredEntities;

	public Storage(String path) {
		LOG.info("Opening Semantic View database.");
		this.db = DBMaker
				.fileDB(new File(path))
				.fileMmapEnable()
				.closeOnJvmShutdown()
		        .make();
		this.ignoredEntities = (IndexTreeList<String>) db.indexTreeList("ignoredEntities", Serializer.STRING).createOrOpen();
	}
}
