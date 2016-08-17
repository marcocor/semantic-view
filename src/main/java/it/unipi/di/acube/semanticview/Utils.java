package it.unipi.di.acube.semanticview;

import java.io.Serializable;
import java.util.Comparator;
import java.util.TreeSet;

import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

public class Utils {
	public static <K extends Comparable<? super K>> Comparator<Entry<K>> comparingEntryByCount() {
		return (Comparator<Entry<K>> & Serializable) (e1, e2) -> Integer.compare(e1.getCount(), e2.getCount());
	}

	public static <E extends Comparable<? super E>> TreeSet<Entry<E>> getTopK(Multiset<E> s, int k) {
		Comparator<Entry<E>> comp = comparingEntryByCount();
		TreeSet<Entry<E>> res = new TreeSet<>(comp);
		for (Entry<E> e : s.entrySet()) {
			if (res.size() < k)
				res.add(e);
			else {
				Entry<E> lowest = res.first();
				if (lowest.getCount() < e.getCount()) {
					res.remove(lowest);
					res.add(e);
				}
			}
		}
		return res;
	}
}
