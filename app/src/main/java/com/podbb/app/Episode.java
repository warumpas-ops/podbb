package com.podbb.app;

public class Episode {
    public long id;
    public long podcastId;
    public String guid;
    public String title;
    public String description;
    public String audioUrl;
    public long pubDate;
    public int durationSeconds;
    public String filePath;
    public boolean downloaded;
    public boolean listened;
    public int positionSeconds;
    public String podcastTitle;    // transient, for display
    public String imageUrl;        // transient, episode-level art (itunes:image)
    public String transcriptUrl;   // transient, from <podcast:transcript url="...">
    public String transcriptType;  // transient, e.g. "text/vtt", "application/json"
    public String chaptersUrl;    // transient, from <podcast:chapters url="...">

    public Episode() {}

    public String getFormattedDuration() {
        if (durationSeconds <= 0) return "";
        int h = durationSeconds / 3600;
        int m = (durationSeconds % 3600) / 60;
        int s = durationSeconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }
}
