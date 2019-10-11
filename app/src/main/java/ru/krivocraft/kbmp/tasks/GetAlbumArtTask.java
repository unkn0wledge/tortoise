package ru.krivocraft.kbmp.tasks;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import ru.krivocraft.kbmp.core.track.Track;
import ru.krivocraft.kbmp.core.utils.BitmapUtils;

public class GetAlbumArtTask extends AsyncTask<Track, Integer, Bitmap> {

    private OnAlbumArtAcquiredCallback callback;

    public GetAlbumArtTask(OnAlbumArtAcquiredCallback callback) {
        this.callback = callback;
    }

    @Override
    protected Bitmap doInBackground(Track... tracks) {
        for (Track track : tracks) {
            Bitmap bitmap = BitmapUtils.loadArt(track.getPath());
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        callback.onAlbumArtAcquired(bitmap);
    }
}
