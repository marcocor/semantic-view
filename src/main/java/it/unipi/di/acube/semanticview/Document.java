package it.unipi.di.acube.semanticview;

import java.time.LocalDateTime;

public class Document {
	public String key, title, body;
	public LocalDateTime date;

	public Document(String key, String title, String body, LocalDateTime date) {
		this.key = key;
		this.title = title;
		this.body = body;
		this.date = date;
	}
}
