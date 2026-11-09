package com.podbb.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "podbb.db";
    private static final int DB_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE podcasts (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, author TEXT, description TEXT, image_url TEXT, feed_url TEXT, itunes_id TEXT, subscribed_at INTEGER)");
        db.execSQL("CREATE TABLE episodes (id INTEGER PRIMARY KEY AUTOINCREMENT, podcast_id INTEGER, guid TEXT, title TEXT, description TEXT, audio_url TEXT, pub_date INTEGER, duration_seconds INTEGER, file_path TEXT, downloaded INTEGER DEFAULT 0, listened INTEGER DEFAULT 0, position_seconds INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE queue (id INTEGER PRIMARY KEY AUTOINCREMENT, episode_id INTEGER, sort_order INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS podcasts");
        db.execSQL("DROP TABLE IF EXISTS episodes");
        db.execSQL("DROP TABLE IF EXISTS queue");
        onCreate(db);
    }

    public long insertPodcast(Podcast p) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", p.title);
        cv.put("author", p.author);
        cv.put("description", p.description);
        cv.put("image_url", p.imageUrl);
        cv.put("feed_url", p.feedUrl);
        cv.put("itunes_id", p.itunesId);
        cv.put("subscribed_at", System.currentTimeMillis());
        return db.insert("podcasts", null, cv);
    }

    public void deletePodcast(long podcastId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("podcasts", "id=?", new String[]{String.valueOf(podcastId)});
        db.delete("episodes", "podcast_id=?", new String[]{String.valueOf(podcastId)});
    }

    public List<Podcast> getAllSubscribed() {
        List<Podcast> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("podcasts", null, null, null, null, null, "subscribed_at DESC");
        while (c.moveToNext()) {
            Podcast p = new Podcast();
            p.id = c.getLong(c.getColumnIndex("id"));
            p.title = c.getString(c.getColumnIndex("title"));
            p.author = c.getString(c.getColumnIndex("author"));
            p.description = c.getString(c.getColumnIndex("description"));
            p.imageUrl = c.getString(c.getColumnIndex("image_url"));
            p.feedUrl = c.getString(c.getColumnIndex("feed_url"));
            p.itunesId = c.getString(c.getColumnIndex("itunes_id"));
            p.subscribedAt = c.getLong(c.getColumnIndex("subscribed_at"));
            p.isSubscribed = true;
            list.add(p);
        }
        c.close();
        return list;
    }

    public Podcast getPodcastByItunesId(String itunesId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("podcasts", null, "itunes_id=?", new String[]{itunesId}, null, null, null);
        Podcast p = null;
        if (c.moveToFirst()) {
            p = new Podcast();
            p.id = c.getLong(c.getColumnIndex("id"));
            p.title = c.getString(c.getColumnIndex("title"));
            p.author = c.getString(c.getColumnIndex("author"));
            p.feedUrl = c.getString(c.getColumnIndex("feed_url"));
            p.itunesId = c.getString(c.getColumnIndex("itunes_id"));
            p.isSubscribed = true;
        }
        c.close();
        return p;
    }

    public Podcast getPodcastByFeedUrl(String feedUrl) {
        if (feedUrl == null || feedUrl.trim().isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("podcasts", null, "feed_url=?", new String[]{feedUrl.trim()}, null, null, null);
        Podcast p = null;
        if (c.moveToFirst()) {
            p = new Podcast();
            p.id = c.getLong(c.getColumnIndex("id"));
            p.title = c.getString(c.getColumnIndex("title"));
            p.author = c.getString(c.getColumnIndex("author"));
            p.description = c.getString(c.getColumnIndex("description"));
            p.imageUrl = c.getString(c.getColumnIndex("image_url"));
            p.feedUrl = c.getString(c.getColumnIndex("feed_url"));
            p.itunesId = c.getString(c.getColumnIndex("itunes_id"));
            p.isSubscribed = true;
        }
        c.close();
        return p;
    }

    public void upsertEpisodes(long podcastId, List<Episode> episodes) {
        SQLiteDatabase db = getWritableDatabase();
        for (Episode e : episodes) {
            Cursor c = db.query("episodes", new String[]{"id", "downloaded", "listened", "position_seconds"},
                    "podcast_id=? AND guid=?", new String[]{String.valueOf(podcastId), e.guid != null ? e.guid : e.audioUrl}, null, null, null);
            if (c.moveToFirst()) {
                ContentValues cv = new ContentValues();
                cv.put("title", e.title);
                cv.put("description", e.description);
                cv.put("audio_url", e.audioUrl);
                cv.put("pub_date", e.pubDate);
                cv.put("duration_seconds", e.durationSeconds);
                db.update("episodes", cv, "id=?", new String[]{String.valueOf(c.getLong(0))});
            } else {
                ContentValues cv = new ContentValues();
                cv.put("podcast_id", podcastId);
                cv.put("guid", e.guid);
                cv.put("title", e.title);
                cv.put("description", e.description);
                cv.put("audio_url", e.audioUrl);
                cv.put("pub_date", e.pubDate);
                cv.put("duration_seconds", e.durationSeconds);
                cv.put("downloaded", 0);
                cv.put("listened", 0);
                cv.put("position_seconds", 0);
                db.insert("episodes", null, cv);
            }
            c.close();
        }
    }

    public List<Episode> getEpisodesForPodcast(long podcastId) {
        List<Episode> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("episodes", null, "podcast_id=?", new String[]{String.valueOf(podcastId)}, null, null, "pub_date DESC");
        while (c.moveToNext()) {
            Episode e = new Episode();
            e.id = c.getLong(c.getColumnIndex("id"));
            e.podcastId = c.getLong(c.getColumnIndex("podcast_id"));
            e.guid = c.getString(c.getColumnIndex("guid"));
            e.title = c.getString(c.getColumnIndex("title"));
            e.description = c.getString(c.getColumnIndex("description"));
            e.audioUrl = c.getString(c.getColumnIndex("audio_url"));
            e.pubDate = c.getLong(c.getColumnIndex("pub_date"));
            e.durationSeconds = c.getInt(c.getColumnIndex("duration_seconds"));
            e.filePath = c.getString(c.getColumnIndex("file_path"));
            e.downloaded = c.getInt(c.getColumnIndex("downloaded")) == 1;
            e.listened = c.getInt(c.getColumnIndex("listened")) == 1;
            e.positionSeconds = c.getInt(c.getColumnIndex("position_seconds"));
            list.add(e);
        }
        c.close();
        return list;
    }

    public Podcast getPodcast(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("podcasts", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Podcast p = null;
        if (c.moveToFirst()) {
            p = new Podcast();
            p.id = c.getLong(c.getColumnIndex("id"));
            p.title = c.getString(c.getColumnIndex("title"));
            p.author = c.getString(c.getColumnIndex("author"));
            p.description = c.getString(c.getColumnIndex("description"));
            p.imageUrl = c.getString(c.getColumnIndex("image_url"));
            p.feedUrl = c.getString(c.getColumnIndex("feed_url"));
            p.itunesId = c.getString(c.getColumnIndex("itunes_id"));
            p.subscribedAt = c.getLong(c.getColumnIndex("subscribed_at"));
            p.isSubscribed = true;
        }
        c.close();
        return p;
    }

    public Episode getEpisode(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("episodes", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Episode e = null;
        if (c.moveToFirst()) {
            e = new Episode();
            e.id = c.getLong(c.getColumnIndex("id"));
            e.podcastId = c.getLong(c.getColumnIndex("podcast_id"));
            e.guid = c.getString(c.getColumnIndex("guid"));
            e.title = c.getString(c.getColumnIndex("title"));
            e.description = c.getString(c.getColumnIndex("description"));
            e.audioUrl = c.getString(c.getColumnIndex("audio_url"));
            e.pubDate = c.getLong(c.getColumnIndex("pub_date"));
            e.durationSeconds = c.getInt(c.getColumnIndex("duration_seconds"));
            e.filePath = c.getString(c.getColumnIndex("file_path"));
            e.downloaded = c.getInt(c.getColumnIndex("downloaded")) == 1;
            e.listened = c.getInt(c.getColumnIndex("listened")) == 1;
            e.positionSeconds = c.getInt(c.getColumnIndex("position_seconds"));
        }
        c.close();
        return e;
    }

    public List<Episode> getDownloadedEpisodes() {
        List<Episode> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT e.*, p.title as podcast_title, p.image_url as podcast_image FROM episodes e JOIN podcasts p ON e.podcast_id = p.id WHERE e.downloaded=1 ORDER BY e.pub_date DESC";
        Cursor c = db.rawQuery(sql, null);
        while (c.moveToNext()) {
            Episode e = new Episode();
            e.id = c.getLong(c.getColumnIndex("id"));
            e.podcastId = c.getLong(c.getColumnIndex("podcast_id"));
            e.title = c.getString(c.getColumnIndex("title"));
            e.audioUrl = c.getString(c.getColumnIndex("audio_url"));
            e.filePath = c.getString(c.getColumnIndex("file_path"));
            e.durationSeconds = c.getInt(c.getColumnIndex("duration_seconds"));
            e.downloaded = true;
            e.podcastTitle = c.getString(c.getColumnIndex("podcast_title"));
            e.imageUrl = c.getString(c.getColumnIndex("podcast_image"));
            list.add(e);
        }
        c.close();
        return list;
    }

    public void markDownloaded(long episodeId, String filePath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("downloaded", 1);
        cv.put("file_path", filePath);
        db.update("episodes", cv, "id=?", new String[]{String.valueOf(episodeId)});
    }

    public void deleteDownload(long episodeId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("downloaded", 0);
        cv.put("file_path", (String) null);
        db.update("episodes", cv, "id=?", new String[]{String.valueOf(episodeId)});
    }

    public void updatePosition(long episodeId, int positionSeconds) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("position_seconds", positionSeconds);
        db.update("episodes", cv, "id=?", new String[]{String.valueOf(episodeId)});
    }

    public void markListened(long episodeId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("listened", 1);
        db.update("episodes", cv, "id=?", new String[]{String.valueOf(episodeId)});
    }
}
