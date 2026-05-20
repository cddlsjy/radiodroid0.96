package net.programmierecke.radiodroid2;

import android.content.Context;
import android.util.Log;

/**
 * 自定义电台管理器
 * 用于管理通过 M3U 文件导入的自定义电台列表
 * 继承自 StationSaveManager，使用独立的存储键
 */
public class CustomStationManager extends StationSaveManager {
    private static final String TAG = "CustomStationManager";
    
    // 自定义电台列表的 SharedPreferences 存储键
    private static final String CUSTOM_STATIONS_KEY = "custom_stations";

    public CustomStationManager(Context ctx) {
        super(ctx);
        Log.d(TAG, "CustomStationManager initialized");
    }

    @Override
    protected String getSaveId() {
        // 使用独立的存储键，与收藏和历史记录区分开
        return CUSTOM_STATIONS_KEY;
    }
}