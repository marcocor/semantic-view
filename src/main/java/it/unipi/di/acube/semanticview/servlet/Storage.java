package it.unipi.di.acube.semanticview.servlet;

import it.unipi.di.acube.semanticview.Tag;
import it.unipi.di.acube.semanticview.Document;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.stream.IntStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.IndexTreeList;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Storage {
	public final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public final Map<String, Document> keyToDocs = new HashMap<>();
	public final Map<String, Set<Tag>> keyToEntities = new HashMap<>();
	public final Map<String, List<String>> entityToKeys = new HashMap<>();
	private DB db;
	public IndexTreeList<String> ignoredEntities;
	public final File uploadDirectory;
	private final File documentDirectory;
	private final File entityDirectory;
	private final File storagePath;
	private final File storageDirectory;
	public static final String DATETIME_FILE_PREFIX_FORMAT = "yyyy-MM-dd-HH-mm-ss.SSS-";
	private static final String DOCUMENTS_DIR = "docs";
	private static final String ENTITIES_DIR = "entities";

	public static List<String> orderedIntersection(List<List<String>> orderedLists) {
		int[] indexes = new int[orderedLists.size()];
		List<String> orderedResult = new Vector<>();
		String max = null;

		while (IntStream.range(0, indexes.length).allMatch(i -> indexes[i] < orderedLists.get(i).size()))
			for (int i = 0; i < orderedLists.size(); i++) {
					max = orderedLists.get(i).get(indexes[i]);
				List<String> listI = orderedLists.get(i);
				while (indexes[i] < listI.size() && max.compareTo(listI.get(indexes[i])) > 0)
					indexes[i]++;
				if (indexes[i] == listI.size())
					break;
				if (indexes[i] < listI.size() && !max.equals(listI.get(indexes[i]))) {
					max = listI.get(indexes[i]);
					break;
				}
				while (indexes[i] + 1 < listI.size() && max.equals(listI.get(indexes[i] + 1)))
					indexes[i]++;
				if (i == orderedLists.size() - 1) {
					orderedResult.add(max);
					indexes[0]++;
				}
			}
		return orderedResult;
	}

	public Storage(String storageDirectory, String storagePath, String uploadDirectory) throws FileNotFoundException, IOException {
		this.documentDirectory = new File(storageDirectory, DOCUMENTS_DIR);
		this.entityDirectory = new File(storageDirectory, ENTITIES_DIR);
		this.storageDirectory = new File(storageDirectory);
		this.storagePath = new File(storagePath);
		this.uploadDirectory = new File(uploadDirectory);
		load();
	}

	public File saveUploadedFile(String filename, InputStream is) throws FileNotFoundException, IOException {
		String date = DateFormatUtils.format(new Date(), DATETIME_FILE_PREFIX_FORMAT);
		File output = new File(uploadDirectory, date + filename);
		IOUtils.copy(is, new FileOutputStream(output));
		return output;
	}

	public void load() throws FileNotFoundException, IOException {
		LOG.info("Opening Semantic View database.");
		this.db = DBMaker.fileDB(storagePath).fileMmapEnable().closeOnJvmShutdown().make();
		this.ignoredEntities = (IndexTreeList<String>) db.indexTreeList("ignoredEntities", Serializer.STRING).createOrOpen();

		LOG.info("Reading documents from {}, entities from {}, storing in {}", documentDirectory, entityDirectory, storagePath);
		File[] documentFileNames = documentDirectory.listFiles();
		File[] entitiesFileNames = entityDirectory.listFiles();

		for (File docFileName : documentFileNames) {
			CSVParser docParser = new CSVParser(new FileReader(docFileName), CSVFormat.DEFAULT.withHeader("key", "title", "body", "time"));
			for (CSVRecord docRecord : docParser) {
				String key = docRecord.get("key");
				String title = docRecord.get("title");
				String body = docRecord.get("body");
				LocalDateTime time = LocalDateTime.parse(docRecord.get("time"), DateTimeFormatter.ISO_DATE_TIME);
				keyToDocs.put(key, new Document(key, title, body, time));
			}
			docParser.close();
		}

		long nEntities = 0;
		for (File entityFileName : entitiesFileNames) {
			CSVParser entityParser = new CSVParser(new FileReader(entityFileName), CSVFormat.DEFAULT.withHeader("key", "entity", "score", "time"));
			for (CSVRecord entityRecord : entityParser) {
				String key = entityRecord.get("key");
				String title = entityRecord.get("entity");
				float score = Float.parseFloat(entityRecord.get("score"));
				if (!keyToEntities.containsKey(key))
					keyToEntities.put(key, new HashSet<>());
				keyToEntities.get(key).add(new Tag(key, title, score));
				if (!entityToKeys.containsKey(title))
					entityToKeys.put(title, new Vector<>());
				entityToKeys.get(title).add(key);
				nEntities++;
			}
			entityParser.close();
		}

		for (List<String> docs: entityToKeys.values())
			Collections.sort(docs);

		LOG.info("Loaded {} documents ({} with entities), {} entities", keyToDocs.size(), keyToEntities.size(), nEntities);

		if (!this.uploadDirectory.isDirectory() || !this.uploadDirectory.canWrite())
			throw new IllegalArgumentException(uploadDirectory + " is not a directory (does not exist?)");
	}

	public void shutdown() {
		keyToDocs.clear();
		keyToEntities.clear();
		db.close();
	}

	public void update() throws FileNotFoundException, IOException {
		// TODO: do it more intelligently
		shutdown();
		load();
	}

	public File getStorageDir() {
		return storageDirectory;
	}
}
