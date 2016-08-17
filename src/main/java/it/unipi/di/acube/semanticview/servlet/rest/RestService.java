package it.unipi.di.acube.semanticview.servlet.rest;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.*;
import org.glassfish.jersey.media.multipart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

import it.unipi.di.acube.semanticview.Tag;
import it.unipi.di.acube.semanticview.Utils;
import it.unipi.di.acube.semanticview.importers.JiraImporter;
import it.unipi.di.acube.semanticview.servlet.Storage;

/**
 * @author Marco Cornolti
 */

@Path("/")
public class RestService {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final float DISCARD_OUTLIER_RATIO = 0.01f;

	@Context
	ServletContext context;

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

	private static boolean filterDocumentByDate(String k, LocalDate begin, LocalDate end, Storage s) {
		if (begin != null)
			if (s.keyToDocs.get(k).date.toLocalDate().isBefore(begin))
				return false;
		if (end != null)
			if (s.keyToDocs.get(k).date.toLocalDate().isAfter(end))
				return false;
		return true;
	}

	@GET
	@Path("/document")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getDocument(@QueryParam("docId") String documentId) throws JSONException {
		Storage s = (Storage) context.getAttribute("storage");
		JSONObject result = new JSONObject();
		result.put("body", s.keyToDocs.get(documentId).body);
		result.put("title", s.keyToDocs.get(documentId).title);
		return result.toString();
	}

	@GET
	@Path("/ignore")
	@Produces({ MediaType.APPLICATION_JSON })
	public String ignoreEntity(@QueryParam("entity") String entity) throws JSONException {
		Storage s = (Storage) context.getAttribute("storage");
		if (entity != null && !entity.isEmpty())
			s.addIgnoredEntities(entity);
		JSONObject result = new JSONObject();
		JSONArray ignoredEntitiesJson = new JSONArray();
		for (String e : s.getIgnoredEntities().stream().sorted().collect(Collectors.toList()))
			ignoredEntitiesJson.put(e);
		result.put("ignoredEntities", ignoredEntitiesJson);
		return result.toString();
	}

	@GET
	@Path("/unignore")
	@Produces({ MediaType.APPLICATION_JSON })
	public String unignoreEntity(@QueryParam("entity") String entity) throws JSONException {
		Storage s = (Storage) context.getAttribute("storage");
		s.unignoreEntity(entity);
		return ignoreEntity(null);
	}

	@GET
	@Path("/time-frequency")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getTimeFrequency(@QueryParam("entities") String entitiesStr,
	        @QueryParam("dateRange") @DefaultValue("~") String dateRangeStr) throws JSONException {
		String[] entities = parseEntities(entitiesStr);
		LocalDate begin = parseBeginDate(dateRangeStr);
		LocalDate end = parseEndDate(dateRangeStr);

		Storage s = (Storage) context.getAttribute("storage");

		HashMap<String, Multiset<LocalDate>> entityToDateCount = new HashMap<>();
		if (entities.length == 0)
			entityToDateCount.put("all", HashMultiset.create());
		for (String entity : entities)
			entityToDateCount.put(entity, HashMultiset.create());

		for (String key : s.keyToEntities.keySet().stream().filter(k -> filterDocumentByDate(k, begin, end, s))
		        .collect(Collectors.toList())) {
			Set<Tag> tags = s.keyToEntities.get(key);
			if (entities.length > 0)
				for (String entity : entities) {
					if (tags.stream().anyMatch(a -> a.entityTitle.equals(entity)))
						entityToDateCount.get(entity).add(s.keyToDocs.get(key).date.toLocalDate());
				}
			else
				entityToDateCount.get("all").add(s.keyToDocs.get(key).date.toLocalDate());
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

		Storage s = (Storage) context.getAttribute("storage");

		Collection<String> matchedKeys = null;
		if (entities.length > 0) {
			List<List<String>> docListsToIntersect = new Vector<>();
			for (String entity : entities)
				if (!s.isIgnoredEntity(entity))
					docListsToIntersect.add(s.entityToKeys.get(entity));

			matchedKeys = Storage.orderedIntersection(docListsToIntersect);
		} else
			matchedKeys = s.keyToEntities.keySet();

		matchedKeys = matchedKeys.stream().filter(k -> filterDocumentByDate(k, begin, end, s)).sorted()
		        .collect(Collectors.toList());

		Multiset<String> frequencies = HashMultiset.create();
		for (String key : matchedKeys) {
			Set<Tag> tags = s.keyToEntities.get(key);
			for (Tag t : tags)
				frequencies.add(t.entityTitle);
		}
		frequencies.removeIf(e -> s.isIgnoredEntity(e));

		JSONArray jsonKeys = new JSONArray();
		matchedKeys.forEach(key -> jsonKeys.put(key));

		JSONArray jsonFrequencies = new JSONArray();

		TreeSet<Entry<String>> sortedFreqs = Utils.getTopK(frequencies, limit);
		
		for (Multiset.Entry<String> entry : sortedFreqs.descendingSet()) {
			JSONObject jsonFrequency = new JSONObject();
			jsonFrequency.put("entity", entry.getElement());
			jsonFrequency.put("frequency", entry.getCount());
			jsonFrequencies.put(jsonFrequency);
		}

		JSONObject result = new JSONObject();
		result.put("frequencies", jsonFrequencies);
		result.put("document_ids", jsonKeys);
		result.put("entities", frequencies.size());
		result.put("documents", jsonKeys.length());

		return result.toString();
	}

	@POST
	@Path("/upload-jira")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({ MediaType.APPLICATION_JSON })
	public String uploadFile(@DefaultValue("true") @FormDataParam("enabled") boolean enabled,
	        @FormDataParam("files") InputStream uploadedInputStream,
	        @FormDataParam("files") FormDataContentDisposition fileDetail)
	                throws JSONException, IOException, InterruptedException {
		LOG.info("Uploading file: {}", fileDetail.getFileName());

		Storage s = (Storage) context.getAttribute("storage");

		File uploadedFile = s.saveUploadedFile(fileDetail.getFileName(), uploadedInputStream);
		JiraImporter.importFile(uploadedFile,
		        context.getInitParameter("it.unipi.di.acube.semanticview.annotate-jira-python-script"), s.getStorageDir(),
		        context.getInitParameter("it.unipi.di.acube.semanticview.tagme-token"),
		        context.getInitParameter("it.unipi.di.acube.semanticview.annotation-lang"));

		synchronized (s) {
			s.update();
		}

		JSONObject result = new JSONObject();
		JSONArray filesJson = new JSONArray();

		JSONObject fileJson = new JSONObject();
		fileJson.put("name", fileDetail.getFileName());
		filesJson.put(fileJson);

		result.put("files", filesJson);
		return result.toString();
	}
}
