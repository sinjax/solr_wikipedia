package com.evi.wikipedia.solr;

/**
 *
 */
public class WikiPage {

	private String id;
	private String body;
	

	private String date;
	private String title;
	private String url;

	public static class Builder {
		
		private WikiPage building;
		public Builder() {
			this.building = new WikiPage();
		}
		public Builder withId(String id) {
			building.id = id;
			return this;
		}
		public Builder withBody(String body) {
			this.building.body = body;
			return this;
		}
		public Builder withDate(String date) {
			this.building.date = date;
			return this;
		}
		public Builder withTitle(String title) {
			this.building.title = title;
			return this;
		}
		public Builder withURL(String url) {
			this.building.url = url;
			return this;
		}
		public WikiPage build() {
			return this.building;
		}

	}

	public String getId() {
		return id;
	}
	public String getBody() {
		return body;
	}

	public String getDate() {
		return date;
	}

	public String getTitle() {
		return title;
	}

	public String getUrl() {
		return url;
	}
	

}
