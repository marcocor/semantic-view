package it.unipi.di.acube.semanticview.servlet;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.junit.Test;
import static org.junit.Assert.*;

public class StorageTest {

	@Test
	public void testOrderedIntersection() throws Exception {
		{
			List<List<String>> orderedStrings = new Vector<>();
			orderedStrings.add(Arrays.asList("b", "c", "d", "g", "l", "l"));
			orderedStrings.add(Arrays.asList("c", "d", "g", "l", "l"));
			orderedStrings.add(Arrays.asList("b", "c", "d", "g", "l", "l"));
			List<String> intersection = Storage.orderedIntersection(orderedStrings);

			assertEquals(Arrays.asList("c", "d", "g", "l"), intersection);
		}
		{
			List<List<String>> orderedStrings = new Vector<>();
			orderedStrings.add(Arrays.asList("c", "d", "g", "l"));
			orderedStrings.add(Arrays.asList());
			orderedStrings.add(Arrays.asList("b", "c", "d", "g", "l"));
			List<String> intersection = Storage.orderedIntersection(orderedStrings);

			assertEquals(Arrays.asList(), intersection);
		}
		{
			List<List<String>> orderedStrings = new Vector<>();
			orderedStrings.add(Arrays.asList("b", "b", "c", "c", "d", "g", "l"));
			orderedStrings.add(Arrays.asList("c", "d", "g"));
			orderedStrings.add(Arrays.asList("b", "c", "d", "g", "l"));
			List<String> intersection = Storage.orderedIntersection(orderedStrings);

			assertEquals(Arrays.asList("c", "d", "g"), intersection);
		}
		{
			List<List<String>> orderedStrings = new Vector<>();
			orderedStrings.add(Arrays.asList("b", "b", "c", "c", "d", "g", "l"));
			orderedStrings.add(Arrays.asList("c", "d", "m"));
			orderedStrings.add(Arrays.asList("b", "c", "d", "g", "l"));
			List<String> intersection = Storage.orderedIntersection(orderedStrings);

			assertEquals(Arrays.asList("c", "d"), intersection);
		}
	}

}
