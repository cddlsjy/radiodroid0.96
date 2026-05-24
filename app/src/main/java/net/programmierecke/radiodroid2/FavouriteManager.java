package net.programmierecke.radiodroid2;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.programmierecke.radiodroid2.station.DataRadioStation;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

public class FavouriteManager extends StationSaveManager {
    @Override
    protected String getSaveId() {
        return "favourites";
    }

    public FavouriteManager(Context ctx) {
        super(ctx);

        setStationStatusListener((station, favourite) -> {
            Intent local = new Intent();
            local.setAction(DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED);
            local.putExtra(DataRadioStation.RADIO_STATION_UUID, station.StationUuid);
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(local);
        });
    }

    @Override
    public void add(DataRadioStation station) {
        if (!has(station.StationUuid)) {
            super.add(station);
        }
    }

    @Override
    public void restore(DataRadioStation station, int pos) {
        if (!has(station.StationUuid)) {
            super.restore(station, pos);
        }
    }
    
    public void LoadM3USimpleWithFileName(final Reader reader, final String fileName) {
        LoadM3USimpleWithFileName(reader, "", fileName);
    }
    
    public List<DataRadioStation> LoadM3UReader(final Reader reader) {
        return super.LoadM3UReader(reader);
    }

    @Override
    void Load() {
        super.Load();
        updateShortcuts();
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    public void updateShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);

            if (shortcutManager != null) {
                List<ShortcutInfo> shortcuts = new ArrayList<>();

                int shortcutCount = min(4, getList().size());
                for (int i = 0; i < shortcutCount; i++) {
                    DataRadioStation station = getList().get(i);
                    shortcuts.add(Utils.createShortcutForStation(context, station, i));
                }

                shortcutManager.setDynamicShortcuts(shortcuts);
            }
        }
    }
}