# RadioDroid 修改大纲

## 一、离线模式功能

### 1.1 添加设置选项
**文件**: `app/src/main/res/xml/preferences.xml`
```xml
<SwitchPreferenceCompat
    android:key="disable_online_verification"
    android:title="@string/pref_disable_online_verification_title"
    android:summary="@string/pref_disable_online_verification_summary"
    android:defaultValue="false" />
```

### 1.2 添加字符串资源
**文件**: `app/src/main/res/values/strings.xml`
```xml
<string name="pref_disable_online_verification_title">Offline mode</string>
<string name="pref_disable_online_verification_summary">Do not verify stations online; use local M3U data only</string>
```

### 1.3 添加工具方法
**文件**: `app/src/main/java/net/programmierecke/radiodroid2/Utils.java`
```java
public static boolean isOfflineMode(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return prefs.getBoolean("disable_online_verification", false);
}
```

### 1.4 修改网络请求方法
**文件**: `app/src/main/java/net/programmierecke/radiodroid2/Utils.java`
在 `downloadFeedRelative()` 方法开头添加：
```java
if (isOfflineMode(ctx)) return null;
```

---

## 二、全屏模式功能

### 2.1 添加字符串资源
**文件**: `app/src/main/res/values/strings.xml`
```xml
<string name="settings_fullscreen_mode">Fullscreen mode</string>
<string name="settings_fullscreen_mode_default">Default</string>
<string name="settings_fullscreen_mode_simplified">Simplified</string>
<string name="settings_fullscreen_mode_landscape">Landscape</string>
<string name="settings_fullscreen_mode_cover">Cover</string>
```

### 2.2 添加数组配置
**文件**: `app/src/main/res/values/arrays.xml`
```xml
<string-array name="fullscreen_mode_entries">
    <item>@string/settings_fullscreen_mode_default</item>
    <item>@string/settings_fullscreen_mode_simplified</item>
    <item>@string/settings_fullscreen_mode_landscape</item>
    <item>@string/settings_fullscreen_mode_cover</item>
</string-array>
<string-array name="fullscreen_mode_values">
    <item>default</item>
    <item>simplified</item>
    <item>landscape</item>
    <item>cover</item>
</string-array>
```

### 2.3 添加设置选项
**文件**: `app/src/main/res/xml/preferences.xml`
```xml
<ListPreference
    android:defaultValue="default"
    android:entries="@array/fullscreen_mode_entries"
    android:entryValues="@array/fullscreen_mode_values"
    android:key="fullscreen_mode"
    android:title="@string/settings_fullscreen_mode"
    android:summary="%s" />
```

### 2.4 添加模式检测方法
**文件**: `app/src/main/java/net/programmierecke/radiodroid2/FragmentPlayerFull.java`
```java
private boolean isLandscapeModeActive() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    String mode = prefs.getString("fullscreen_mode", "default");
    if ("cover".equals(mode)) {
        int orientation = getResources().getConfiguration().orientation;
        return (orientation == Configuration.ORIENTATION_LANDSCAPE);
    }
    return "landscape".equals(mode);
}

private boolean isSimplifiedModeActive() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    String mode = prefs.getString("fullscreen_mode", "default");
    return "simplified".equals(mode);
}
```

---

## 三、Android 15 文件选择器兼容

**文件**: `app/src/main/AndroidManifest.xml`
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.GET_CONTENT" />
        <data android:mimeType="*/*" />
    </intent>
</queries>
```

---

## 四、内置电台导入

### 4.1 创建内置 M3U 文件
**文件**: `app/src/main/res/raw/collection.m3u`
```
#EXTM3U
#EXTINF:-1,Station Name
http://stream.url
#RADIOBROWSERUUID:uuid-here
#EXTIMG:http://icon.url
```

### 4.2 添加导入逻辑
**文件**: `app/src/main/java/net/programmierecke/radiodroid2/RadioDroidApp.java`
```java
private void importCollectionM3U() {
    try {
        InputStream inputStream = getResources().openRawResource(R.raw.collection);
        InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
        List<DataRadioStation> stations = favouriteManager.LoadM3UReader(reader);
        if (stations != null && !stations.isEmpty()) {
            for (DataRadioStation station : stations) {
                if (!favouriteManager.has(station.StationUuid)) {
                    favouriteManager.add(station);
                }
            }
        }
    } catch (Exception e) {
        Log.e("RadioDroidApp", "Failed to import collection.m3u", e);
    }
}
```

在 `onCreate()` 方法中调用：
```java
importCollectionM3U();
```

---

## 五、自动全屏播放选项

### 5.1 添加设置选项
**文件**: `app/src/main/res/xml/preferences.xml`
```xml
<CheckBoxPreference
    android:defaultValue="false"
    android:key="auto_fullscreen_on_play"
    android:summaryOff="@string/settings_auto_fullscreen_on_play_off"
    android:summaryOn="@string/settings_auto_fullscreen_on_play_on"
    android:title="@string/settings_auto_fullscreen_on_play" />
```

### 5.2 添加字符串资源
**文件**: `app/src/main/res/values/strings.xml`
```xml
<string name="settings_auto_fullscreen_on_play">Auto-fullscreen on play</string>
<string name="settings_auto_fullscreen_on_play_on">Enter fullscreen mode when starting playback</string>
<string name="settings_auto_fullscreen_on_play_off">Do not enter fullscreen mode on playback</string>
```

### 5.3 添加自动全屏逻辑
**文件**: `app/src/main/java/net/programmierecke/radiodroid2/ActivityMain.java`
在广播接收器的 `PLAYER_SERVICE_STATE_CHANGE` 处理中添加：
```java
if (PlayerServiceUtil.isPlaying()) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityMain.this);
    boolean autoFullscreen = prefs.getBoolean("auto_fullscreen_on_play", false);
    if (autoFullscreen) {
        playerBottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
```

---

## 六、自定义电台功能（替换闹钟按钮）

### 6.1 修改底部导航菜单
**文件**: `app/src/main/res/menu/menu_bottom_navigation.xml`
```xml
<item
    android:id="@+id/nav_item_custom"
    android:icon="@drawable/ic_view_icons_24dp"
    android:title="@string/nav_item_custom"
    app:contentDescription="@string/nav_item_custom" />
```

### 6.2 添加字符串资源
**文件**: `app/src/main/res/values/strings.xml`
```xml
<string name="nav_item_custom">Custom</string>
<string name="success_imported_stations_custom">Imported %1$d custom stations from %2$s</string>
<string name="alert_delete_custom">Do you really want to delete all custom stations?</string>
<string name="notify_deleted_custom">The custom station list has been cleared</string>
<string name="action_delete_custom">Delete custom</string>
```

### 6.3 创建 CustomStationManager
**文件**: `app/src/main/java/net/programmierecke/radiodroid2/CustomStationManager.java`
```java
public class CustomStationManager extends StationSaveManager {
    private static final String CUSTOM_STATIONS_KEY = "custom_stations";

    public CustomStationManager(Context ctx) {
        super(ctx);
    }

    @Override
    protected String getSaveId() {
        return CUSTOM_STATIONS_KEY;
    }
}
```

### 6.4 创建 FragmentCustomStations
**文件**: `app/src/main/java/net/programmierecke/radiodroid2/FragmentCustomStations.java`
参考完整代码实现，包含：
- RecyclerView 显示自定义电台列表
- 支持导入/导出 M3U 文件

### 6.5 修改 ActivityMain.java
1. **导航处理**: 在 `onNavigationItemSelected()` 中添加：
```java
} else if (selectedMenuItem == R.id.nav_item_custom) {
    f = new FragmentCustomStations();
}
```

2. **菜单处理**: 在 `onPrepareOptionsMenu()` 中添加：
```java
} else if (selectedMenuItem == R.id.nav_item_custom) {
    menuItemSleepTimer.setVisible(true);
    menuItemSort.setVisible(false);
    menuItemSave.setVisible(true);
    menuItemLoad.setVisible(true);
    menuItemSave.setTitle(R.string.nav_item_save_playlist);
    CustomStationManager customStationManager = new CustomStationManager(this);
    if (!customStationManager.isEmpty()) {
        menuItemDelete.setVisible(true).setTitle(R.string.action_delete_custom);
    } else {
        menuItemDelete.setVisible(false);
    }
    myToolbar.setTitle(R.string.nav_item_custom);
}
```

3. **导入处理**: 在 `onActivityResult()` 的 `ACTION_LOAD_FILE` 处理中添加：
```java
if (selectedMenuItem == R.id.nav_item_custom) {
    CustomStationManager customStationManager = new CustomStationManager(ActivityMain.this);
    importedStations = customStationManager.LoadM3UReader(reader);
}
```

4. **删除处理**: 在 `onOptionsItemSelected()` 的 `action_delete` 处理中添加：
```java
} else if (selectedMenuItem == R.id.nav_item_custom) {
    new AlertDialog.Builder(this, Utils.getAlertDialogThemeResId(this))
            .setMessage(this.getString(R.string.alert_delete_custom))
            .setCancelable(true)
            .setPositiveButton(this.getString(R.string.yes), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    CustomStationManager customStationManager = new CustomStationManager(ActivityMain.this);
                    customStationManager.clear();
                    Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.notify_deleted_custom), Toast.LENGTH_SHORT);
                    toast.show();
                    recreate();
                }
            })
            .setNegativeButton(this.getString(R.string.no), null)
            .show();
}
```

---

## 七、编译与安装

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/free/debug/RadioDroid-free-debug-*.apk
```

---

## 修改文件清单

| 文件路径 | 修改类型 | 说明 |
|---------|---------|------|
| `res/xml/preferences.xml` | 修改 | 添加离线模式、全屏模式、自动全屏选项 |
| `res/values/strings.xml` | 修改 | 添加相关字符串资源 |
| `res/values/arrays.xml` | 修改 | 添加全屏模式数组 |
| `res/menu/menu_bottom_navigation.xml` | 修改 | 替换闹钟按钮为自定义按钮 |
| `res/raw/collection.m3u` | 新建 | 内置电台列表 |
| `AndroidManifest.xml` | 修改 | 添加包可见性配置 |
| `Utils.java` | 修改 | 添加离线模式检测和网络请求拦截 |
| `RadioDroidApp.java` | 修改 | 添加内置电台导入逻辑 |
| `FragmentPlayerFull.java` | 修改 | 添加全屏模式检测方法 |
| `ActivityMain.java` | 修改 | 添加自定义电台导航、菜单、导入、删除逻辑 |
| `CustomStationManager.java` | 新建 | 自定义电台管理器 |
| `FragmentCustomStations.java` | 新建 | 自定义电台列表页面 |