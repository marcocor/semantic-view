package it.unipi.di.acube.semanticview;

public class Annotation {
	public String documentKey, entityTitle;
	public float score;

	public Annotation(String documentKey, String entityTitle, float score) {
		this.documentKey = documentKey;
		this.entityTitle = entityTitle;
		this.score = score;
	}
}
