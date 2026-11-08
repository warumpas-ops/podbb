package com.podbb.app;

import java.util.List;

public class ItunesApi {
    public static List<Podcast> search(String query) throws Exception {
        return PodcastSearchEngine.search(query);
    }

    public static List<Podcast> getPopular() {
        return PodcastSearchEngine.getToplist();
    }
}
