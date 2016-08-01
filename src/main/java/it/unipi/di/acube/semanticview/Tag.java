package it.unipi.di.acube.semanticview;

public class Tag {
	public String documentKey, entityTitle;
	public float score;

	public Tag(String documentKey, String entityTitle, float score) {
		this.documentKey = documentKey;
		this.entityTitle = entityTitle;
		this.score = score;
	}
}
