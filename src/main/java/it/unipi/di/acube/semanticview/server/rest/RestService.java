package it.unipi.di.acube.semanticview.server.rest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.comparators.ReverseComparator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.semanticview.Annotation;
import it.unipi.di.acube.semanticview.Document;

/**
 * @author Marco Cornolti
 */

@Path("/")
public class RestService {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private final static Map<String, Document> keyToDocs = new HashMap<>();
	private final static Map<String, Set<Annotation>> keyToEntities = new HashMap<>();
	private final static Set<String> ignoredEntities = new HashSet<>();

	public static void initialize(String documentDirectory, String entityDirectory) throws FileNotFoundException, IOException {
		LOG.info("Reading documents from {}, entities from {}", documentDirectory, entityDirectory);
		File[] documentFileNames = new File(documentDirectory).listFiles();
		File[] entitiesFileNames = new File(entityDirectory).listFiles();

		for (File docFileName : documentFileNames) {
			CSVParser docParser = new CSVParser(new FileReader(docFileName),
			        CSVFormat.DEFAULT.withHeader("key", "title", "body", "time"));
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
			CSVParser entityParser = new CSVParser(new FileReader(entityFileName),
			        CSVFormat.DEFAULT.withHeader("key", "entity", "score", "time"));
			for (CSVRecord entityRecord : entityParser) {
				String key = entityRecord.get("key");
				String title = entityRecord.get("entity");
				float score = Float.parseFloat(entityRecord.get("score"));
				if (!keyToEntities.containsKey(key))
					keyToEntities.put(key, new HashSet<>());
				keyToEntities.get(key).add(new Annotation(key, title, score));
				nEntities++;
			}
			entityParser.close();
		}
		LOG.info("Loaded {} documents ({} with entities), {} entities", keyToDocs.size(), keyToEntities.size(), nEntities);
	}
	@GET
	@Path("/document")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getDocument(@QueryParam("docId") String documentId) throws JSONException {
		JSONObject result = new JSONObject();
		result.put("body", keyToDocs.get(documentId).body);
		result.put("title", keyToDocs.get(documentId).title);
		return result.toString();
	}
	
	@GET
	@Path("/ignore")
	@Produces({ MediaType.APPLICATION_JSON })
	public String ignoreEntity(@QueryParam("entity") String entity) throws JSONException {
		ignoredEntities.add(entity);
		JSONObject result = new JSONObject();
		result.put("ignored", entity);
		result.put("ignored-entities", ignoredEntities.size());
		return result.toString();
	}
	
	@GET
	@Path("/frequency")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getEntityFrequency(@QueryParam("entities") String entitiesStr, @QueryParam("limit") @DefaultValue("100") String limitStr) throws JSONException {
		int limit = Integer.parseInt(limitStr);
		String[] entities = Arrays.stream(entitiesStr.split("\\$\\$\\$")).filter(e -> !e.isEmpty()).toArray(String[]::new);
		LOG.info("Entities in query ({}): {}", entities.length, entitiesStr);

		List<String> matchedKeys = new Vector<>();
		Map<String, Integer> frequencies = new HashMap<>();
		for (String key : keyToEntities.keySet()) {
			Set<Annotation> annotations = keyToEntities.get(key);
			if (Arrays.stream(entities).filter(e -> !ignoredEntities.contains(e)).allMatch(e -> annotations.stream().filter(a -> !ignoredEntities.contains(a.entityTitle)).anyMatch(a -> a.entityTitle.equals(e)))) {
				matchedKeys.add(key);
				for (Annotation a : annotations)
					if (!ignoredEntities.contains(a.entityTitle))
						if (!frequencies.containsKey(a.entityTitle))
							frequencies.put(a.entityTitle, 1);
						else
							frequencies.put(a.entityTitle, frequencies.get(a.entityTitle) + 1);
			}
		}

		JSONArray jsonKeys = new JSONArray();
		matchedKeys.stream().sorted().forEach(key -> jsonKeys.put(key));

		JSONArray jsonFrequencies = new JSONArray();

		List<Entry<String, Integer>> orderedFreq = new Vector<>(frequencies.entrySet());
		orderedFreq.sort(new ReverseComparator<Entry<String, Integer>>(Map.Entry.comparingByValue()));
		if (limit >= 0)
			orderedFreq = orderedFreq.subList(0, Math.min(limit, orderedFreq.size()));
		for (Entry<String, Integer> entry : orderedFreq) {
			JSONObject jsonFrequency = new JSONObject();
			jsonFrequency.put("entity", entry.getKey());
			jsonFrequency.put("frequency", entry.getValue());
			jsonFrequencies.put(jsonFrequency);
		}
		;

		JSONObject result = new JSONObject();
		result.put("frequencies", jsonFrequencies);
		result.put("document_ids", jsonKeys);
		result.put("entities", frequencies.values().stream().mapToInt(i -> i.intValue()).sum());
		result.put("documents", jsonKeys.length());

		return result.toString();
	}
}
