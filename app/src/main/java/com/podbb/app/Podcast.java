package com.podbb.app;

public class Podcast {
    public long id;
    public String title;
    public String author;
    public String description;
    public String imageUrl;
    public String feedUrl;
    public String itunesId;
    public long subscribedAt;
    public boolean isSubscribed;

    public Podcast() {}

    public Podcast(String title, String author, String description, String imageUrl, String feedUrl, String itunesId) {
        this.title = title;
        this.author = author;
        this.description = description;
        this.imageUrl = imageUrl;
        this.feedUrl = feedUrl;
        this.itunesId = itunesId;
    }
}
