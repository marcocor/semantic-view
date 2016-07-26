package it.unipi.di.acube.semanticview.server.rest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

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
	private static final float DISCARD_OUTLIER_RATIO = 0.01f;

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

	private static String[] parseEntities(String entitiesStr) {
		return Arrays.stream(entitiesStr.split("\\$\\$\\$")).filter(e -> !e.isEmpty()).toArray(String[]::new);
	}

	private static LocalDate parseBeginDate(String dateRangeStr) {
		String[] datesStr = dateRangeStr.split("~");
		return datesStr.length == 0 || datesStr[0].isEmpty() ? null : LocalDate.parse(datesStr[0]);
	}

	private static LocalDate parseEndDate(String dateRangeStr) {
		String[] datesStr = dateRangeStr.split("~");
		return datesStr.length <= 1 || datesStr[1].isEmpty() ? null : LocalDate.parse(datesStr[1]);
	}

	private static List<LocalDate> removeOutliers(Multiset<LocalDate> counter) {
		long count = 0;
		for (LocalDate date : counter.elementSet())
			count += counter.count(date);

		List<LocalDate> ordered = counter.elementSet().stream().sorted().collect(Collectors.toList());
		int begin = 0;
		int discarded = 0;
		while (begin < ordered.size() && (discarded + counter.count(ordered.get(begin)) < count * DISCARD_OUTLIER_RATIO)) {
			discarded += counter.count(ordered.get(begin));
			begin++;
		}

		int end = ordered.size() - 1;
		discarded = 0;
		while (end >= 0 && (discarded + counter.count(ordered.get(end)) < count * DISCARD_OUTLIER_RATIO)) {
			discarded += counter.count(ordered.get(end));
			end--;
		}
		return ordered.subList(begin, end + 1);
	}

	private static boolean filterDocumentByDate(String k, LocalDate begin, LocalDate end) {
		if (begin != null)
			if (keyToDocs.get(k).date.toLocalDate().isBefore(begin))
				return false;
		if (end != null)
			if (keyToDocs.get(k).date.toLocalDate().isAfter(end))
				return false;
		return true;
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
	@Path("/time-frequency")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getTimeFrequency(@QueryParam("entities") String entitiesStr,
	        @QueryParam("dateRange") @DefaultValue("~") String dateRangeStr) throws JSONException {
		String[] entities = parseEntities(entitiesStr);
		LocalDate begin = parseBeginDate(dateRangeStr);
		LocalDate end = parseEndDate(dateRangeStr);
		HashMap<String, Multiset<LocalDate>> entityToDateCount = new HashMap<>();

		if (entities.length == 0)
			entityToDateCount.put("all", HashMultiset.create());
		for (String entity : entities)
			entityToDateCount.put(entity, HashMultiset.create());

		for (String key : keyToEntities.keySet().stream().filter(k -> filterDocumentByDate(k, begin, end))
		        .collect(Collectors.toList())) {
			Set<Annotation> annotations = keyToEntities.get(key);
			if (entities.length > 0)
				for (String entity : entities) {
					if (annotations.stream().anyMatch(a -> a.entityTitle.equals(entity)))
						entityToDateCount.get(entity).add(keyToDocs.get(key).date.toLocalDate());
				}
			else
				entityToDateCount.get("all").add(keyToDocs.get(key).date.toLocalDate());
		}

		JSONArray result = new JSONArray();
		for (String entity : entityToDateCount.keySet()) {
			Multiset<LocalDate> count = entityToDateCount.get(entity);
			JSONObject entityArray = new JSONObject();
			result.put(entityArray);
			entityArray.put("label", entity);
			JSONArray frequencies = new JSONArray();
			List<LocalDate> sortedDates = removeOutliers(count);
			for (LocalDate date : sortedDates) {
				JSONArray frequencyElement = new JSONArray();
				frequencyElement.put(date.atTime(0, 0).toEpochSecond(ZoneOffset.UTC) * 1000);
				frequencyElement.put(count.count(date));
				frequencies.put(frequencyElement);
			}
			;
			entityArray.put("data", frequencies);
		}

		return result.toString();
	}

	@GET
	@Path("/frequency")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getEntityFrequency(@QueryParam("entities") String entitiesStr,
	        @QueryParam("limit") @DefaultValue("100") String limitStr,
	        @QueryParam("dateRange") @DefaultValue("~") String dateRangeStr) throws JSONException {
		int limit = Integer.parseInt(limitStr);
		String[] entities = parseEntities(entitiesStr);
		LocalDate begin = parseBeginDate(dateRangeStr);
		LocalDate end = parseEndDate(dateRangeStr);

		LOG.info("Entities in query ({}): {}", entities.length, entitiesStr);

		List<String> matchedKeys = new Vector<>();
		Map<String, Integer> frequencies = new HashMap<>();
		for (String key : keyToEntities.keySet().stream().filter(k -> filterDocumentByDate(k, begin, end))
		        .collect(Collectors.toList())) {
			Set<Annotation> annotations = keyToEntities.get(key);
			if (Arrays.stream(entities).filter(e -> !ignoredEntities.contains(e)).allMatch(e -> annotations.stream()
			        .filter(a -> !ignoredEntities.contains(a.entityTitle)).anyMatch(a -> a.entityTitle.equals(e)))) {
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
