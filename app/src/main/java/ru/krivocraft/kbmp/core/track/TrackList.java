package ru.krivocraft.kbmp.core.track;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TrackList {

    public static final String EXTRA_TRACK_LIST = "track_list_extra";

    public static final int TRACK_LIST_CUSTOM = 91;
    public static final int TRACK_LIST_BY_AUTHOR = 92;
    public static final int TRACK_LIST_BY_TAG = 93;

    public static final String LOOP_TYPE = "loop_type";
    public static final int LOOP_TRACK = 122;
    public static final int LOOP_TRACK_LIST = 123;
    public static final int NOT_LOOP = 124;

    private static final String TABLE_PREFIX = "l";
    private String displayName;
    private boolean shuffled = false;
    private final int type;
    private String identifier;

    private List<TrackReference> tracksReferences;

    private List<TrackReference> shuffleCache;

    public TrackList(String displayName, List<TrackReference> tracksReferences, int type, String identifier) {
        this.displayName = displayName;
        this.tracksReferences = tracksReferences;
        this.type = type;
        this.identifier = identifier;
    }

    public TrackList(String displayName, List<TrackReference> tracksReferences, int type) {
        this(displayName, tracksReferences, type, createIdentifier(displayName));
    }

    @NonNull
    public static String createIdentifier(String displayName) {
        StringBuilder sb = new StringBuilder(TABLE_PREFIX);
        for (int i = 0; i < displayName.length(); i++) {
            sb.append(Integer.parseInt(String.valueOf(displayName.codePointAt(i)), 16));
        }
        return sb.toString();
    }

    public int shuffle(TrackReference currentTrack) {
        if (!isShuffled()) {
            shuffleCache = new ArrayList<>(tracksReferences);
            tracksReferences.remove(currentTrack);

            Collections.shuffle(tracksReferences);

            List<TrackReference> shuffled = new ArrayList<>();
            shuffled.add(currentTrack);
            shuffled.addAll(tracksReferences);
            tracksReferences = shuffled;
            setShuffled(true);
            return 0;
        } else {
            tracksReferences = new ArrayList<>(shuffleCache);
            shuffleCache = null;
            setShuffled(false);
            return indexOf(currentTrack);
        }

    }

    public int indexOf(TrackReference item) {
        return tracksReferences.indexOf(item);
    }

    public int size() {
        return tracksReferences.size();
    }

    public TrackReference get(int index) {
        return tracksReferences.get(index);
    }

    public String getIdentifier() {
        return identifier;
    }

    public List<TrackReference> getTrackReferences() {
        return tracksReferences;
    }

    public void removeAll(Collection<TrackReference> trackReferences) {
        tracksReferences.removeAll(trackReferences);
    }

    public void remove(TrackReference track) {
        tracksReferences.remove(track);
    }

    public void add(TrackReference trackReference) {
        tracksReferences.add(trackReference);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static TrackList fromJson(String json) {
        return new Gson().fromJson(json, TrackList.class);
    }

    public boolean isShuffled() {
        return shuffled;
    }

    private void setShuffled(boolean shuffled) {
        this.shuffled = shuffled;
    }

    public int getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackList trackList = (TrackList) o;
        return type == trackList.type &&
                displayName.equals(trackList.displayName) &&
                tracksReferences.equals(trackList.tracksReferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, type, tracksReferences);
    }

}