# RadioDroid 0.96 代码修改详细大纲

## 修改日期：2026-05-21

***

## 一、修改概述

本次修改针对 RadioDroid 0.96 版本进行了以下功能修复：

1. **问题1**: 低版本 Android (6/8) 系统上自定义电台和收藏的 M3U 文件导入/导出菜单灰色不可用
2. **问题2**: 全屏播放器上一个/下一个按钮总是在历史电台内切换，而不是在当前显示分类内切换
3. **问题3**: Android 6.0 上 Lambda 表达式和 `Comparator.reversed()` 方法不兼容导致闪退

***

## 二、修改文件清单

|  序号 | 文件路径                         |       修改类型      | 说明                     |
| :-: | :--------------------------- | :-------------: | :--------------------- |
|  1  | `HistoryManager.java`        |      核心逻辑修复     | 保留电台 queue 引用          |
|  2  | `ActivityMain.java`          | 兼容模式增强 + SAF 支持 | M3U 导入/导出 + 自定义电台支持    |
|  3  | `FragmentLocalStations.java` |   Java 8 兼容性修复  | 移除 Lambda 和 reversed() |
|  4  | `FragmentSettings.java`      |     离线模式导入修复    | Context 生命周期安全         |

***

## 三、详细修改说明

### 3.1 HistoryManager.java - 保留电台 queue 引用

**文件路径**: `app/src/main/java/net/programmierecke/radiodroid2/HistoryManager.java`

**问题根源**: 当电台添加到历史记录时，`super.addFront(station)` 会将 `station.queue` 设置为 `HistoryManager.this`，导致从自定义/收藏列表点击播放的电台，其 `queue` 被强制覆盖为历史管理器，全屏播放器的 next/previous 按钮根据 `station.queue` 决定切换范围，导致总是在历史内切换。

**修改内容**:

```java
@Override
public void add(DataRadioStation station){
    DataRadioStation stationFromHistory = getById(station.StationUuid);
    if (stationFromHistory != null) {
        listStations.remove(stationFromHistory);
        stationFromHistory.queue = station.queue;  // 新增：同步源电台的queue引用
        listStations.add(0, stationFromHistory);
        Save();
        return;
    }

    cutList(MAXSIZE - 1);
    StationSaveManager originalQueue = station.queue;  // 新增：保存原始queue引用
    super.addFront(station);  // 此方法内部会覆盖 station.queue = HistoryManager.this
    if (originalQueue != null) {
        station.queue = originalQueue;  // 新增：恢复原始queue引用
    }
}
```

**效果**:

| 播放来源  | 切换范围                              |
| :---- | :-------------------------------- |
| 自定义电台 | `CustomStationManager` → 自定义列表内切换 |
| 收藏电台  | `FavouriteManager` → 收藏列表内切换      |
| 历史电台  | `HistoryManager` → 历史列表内切换        |

***

### 3.2 ActivityMain.java - 自定义电台完整支持

**文件路径**: `app/src/main/java/net/programmierecke/radiodroid2/ActivityMain.java`

#### 3.2.1 onFileSelected() 自定义电台完整支持

**位置**: `onFileSelected()` 方法

**修改前**: 只处理 `starred` (收藏) 和 `history` (历史)\
**修改后**: 增加 `custom` (自定义电台) 判断分支

```java
@Override
public void onFileSelected(FileDialog dialog, File file) {
    try {
        Log.i("MAIN", "save to " + file.getParent() + "/" + file.getName());
        RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
        FavouriteManager favouriteManager = radioDroidApp.getFavouriteManager();
        HistoryManager historyManager = radioDroidApp.getHistoryManager();
        CustomStationManager customStationManager = new CustomStationManager(this);

        if (dialog instanceof SaveFileDialog) {
            if (selectedMenuItem == R.id.nav_item_starred) {
                favouriteManager.SaveM3U(file.getParent(), file.getName());
            } else if (selectedMenuItem == R.id.nav_item_history) {
                historyManager.SaveM3U(file.getParent(), file.getName());
            } else if (selectedMenuItem == R.id.nav_item_custom) {
                customStationManager.SaveM3U(file.getParent(), file.getName());  // 新增
            }
        } else if (dialog instanceof OpenFileDialog) {
            if (selectedMenuItem == R.id.nav_item_starred) {
                favouriteManager.LoadM3U(file.getParent(), file.getName());
            } else if (selectedMenuItem == R.id.nav_item_custom) {
                customStationManager.LoadM3U(file.getParent(), file.getName());  // 新增
            } else {
                favouriteManager.LoadM3U(file.getParent(), file.getName());
            }
        }
    } catch (Exception e) {
        Log.e("MAIN", e.toString());
    }
}
```

#### 3.2.2 SaveFavourites() 兼容模式 MIME 类型支持

**位置**: `SaveFavourites()` 方法

**修改前**: 直接使用 `audio/x-mpegurl` 类型\
**修改后**: 兼容模式下使用 `*/*` 类型并添加 MIME 类型过滤

```java
void SaveFavourites() {
    FavouriteManager favouriteManager = new FavouriteManager(this);
    int favouriteCount = favouriteManager.getList().size();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    String timestamp = sdf.format(new Date());
    String defaultFileName = "RadioDroid_Favorites_" + timestamp + "_" + favouriteCount + "stations.m3u";
    
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    
    // 兼容模式使用 */* 类型，否则使用 audio/x-mpegurl
    if (Utils.isCompatibilityMode(this)) {
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/x-mpegurl", "text/plain", "*/*"});
    } else {
        intent.setType("audio/x-mpegurl");
    }
    intent.putExtra(Intent.EXTRA_TITLE, defaultFileName);
    startActivityForResult(intent, ACTION_SAVE_FILE);
}
```

#### 3.2.3 LoadFavourites() 兼容模式 MIME 类型支持

**位置**: `LoadFavourites()` 方法

```java
void LoadFavourites() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    
    // 兼容模式使用 */* 类型，否则使用 audio/x-mpegurl
    if (Utils.isCompatibilityMode(this)) {
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/x-mpegurl", "text/plain", "*/*"});
    } else {
        intent.setType("audio/x-mpegurl");
    }
    intent.putExtra(Intent.EXTRA_TITLE, "playlist.m3u");
    startActivityForResult(intent, ACTION_LOAD_FILE);
}
```

#### 3.2.4 onActivityResult() SAF 保存自定义电台支持

**位置**: `onActivityResult()` 方法中的 `ACTION_SAVE_FILE` 处理

```java
boolean success;
if (selectedMenuItem == R.id.nav_item_starred) {
    success = favouriteManager.SaveM3UToStream(outputStream);
} else if (selectedMenuItem == R.id.nav_item_history) {
    success = historyManager.SaveM3UToStream(outputStream);
} else if (selectedMenuItem == R.id.nav_item_custom) {
    CustomStationManager customStationManager = new CustomStationManager(ActivityMain.this);
    success = customStationManager.SaveM3UToStream(outputStream);  // 新增
} else {
    success = false;
}
```

***

### 3.3 FragmentLocalStations.java - Java 8 兼容性修复

**文件路径**: `app/src/main/java/net/programmierecke/radiodroid2/station/FragmentLocalStations.java`

**问题根源**: Android 6.0 (API 23) 不支持以下 Java 8 特性：

- Lambda 表达式 (`Comparator.comparing(s -> ...)`)
- `Comparator.reversed()` 方法

**修改位置**: `getSortedStations()` 方法

**修改前**:

```java
switch (currentSortMode) {
    case SORT_NAME:
        comparator = Comparator.comparing(s -> s.Name.toLowerCase());
        break;
    case SORT_CLICK_COUNT:
        comparator = Comparator.comparingInt(s -> s.ClickCount);
        break;
    case SORT_VOTES:
        comparator = Comparator.comparingInt(s -> s.Votes);
        break;
    case SORT_RECENT:
        comparator = Comparator.comparing(s -> s.LastChangeTime, Comparator.nullsLast(Comparator.reverseOrder()));
        break;
}
if (comparator != null) {
    if (!sortAscending) {
        comparator = comparator.reversed();
    }
    Collections.sort(sorted, comparator);
}
```

**修改后**:

```java
switch (currentSortMode) {
    case SORT_NAME:
        Collections.sort(sorted, new Comparator<DataRadioStation>() {
            @Override
            public int compare(DataRadioStation s1, DataRadioStation s2) {
                String name1 = s1.Name != null ? s1.Name.toLowerCase() : "";
                String name2 = s2.Name != null ? s2.Name.toLowerCase() : "";
                return sortAscending ? name1.compareTo(name2) : name2.compareTo(name1);
            }
        });
        break;
    case SORT_CLICK_COUNT:
        Collections.sort(sorted, new Comparator<DataRadioStation>() {
            @Override
            public int compare(DataRadioStation s1, DataRadioStation s2) {
                int result = Integer.compare(s1.ClickCount, s2.ClickCount);
                return sortAscending ? result : -result;
            }
        });
        break;
    case SORT_VOTES:
        Collections.sort(sorted, new Comparator<DataRadioStation>() {
            @Override
            public int compare(DataRadioStation s1, DataRadioStation s2) {
                int result = Integer.compare(s1.Votes, s2.Votes);
                return sortAscending ? result : -result;
            }
        });
        break;
    case SORT_RECENT:
        Collections.sort(sorted, new Comparator<DataRadioStation>() {
            @Override
            public int compare(DataRadioStation s1, DataRadioStation s2) {
                String time1 = s1.LastChangeTime;
                String time2 = s2.LastChangeTime;
                if (time1 == null && time2 == null) {
                    return 0;
                } else if (time1 == null) {
                    return sortAscending ? 1 : -1;
                } else if (time2 == null) {
                    return sortAscending ? -1 : 1;
                } else {
                    int result = time1.compareTo(time2);
                    return sortAscending ? result : -result;
                }
            }
        });
        break;
}
```

**修复涉及的排序模式**:

- `SORT_NAME` - 按名称排序
- `SORT_CLICK_COUNT` - 按点击次数排序
- `SORT_VOTES` - 按投票数排序
- `SORT_RECENT` - 按更新时间排序

***

### 3.4 FragmentSettings.java - 离线模式导入修复

**文件路径**: `app/src/main/java/net/programmierecke/radiodroid2/FragmentSettings.java`

**问题根源**: 在后台线程中直接使用 `requireContext()` 和 `requireActivity()`，如果 Fragment/Activity 已销毁会导致崩溃。

**修改内容**:

1. **方法开头添加 Context 检查**:

```java
private void importDatabase(Uri uri) {
    Context context = getContext();
    if (context == null) {
        Log.e("FragmentSettings", "Context is null, cannot import database");
        return;
    }
    // ...
    Context finalContext = context;
    Activity finalActivity = getActivity();
    // ...
}
```

1. **添加安全的字符串获取方法**:

```java
private String getStringSafe(int resId) {
    try {
        return getString(resId);
    } catch (Exception e) {
        Log.e("FragmentSettings", "getString failed for resId: " + resId, e);
        return "Error";
    }
}

private String getStringSafe(int resId, Object... formatArgs) {
    try {
        return getString(resId, formatArgs);
    } catch (Exception e) {
        Log.e("FragmentSettings", "getString failed for resId: " + resId, e);
        return "Error";
    }
}
```

1. **在 UI 线程操作前检查 Activity 是否存在**:

```java
if (finalActivity != null) {
    finalActivity.runOnUiThread(() -> {
        // UI 操作
    });
}
```

1. **添加 Activity 导入**:

```java
import android.app.Activity;
```

***

## 四、编译与安装

```bash
# 编译 Debug 版本
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/free/debug/RadioDroid-free-debug-DEV-0.96-No commit hash.apk
```

***

## 五、功能效果总结

| 功能              | 修改前                | 修改后                  |
| :-------------- | :----------------- | :------------------- |
| 全屏播放器切换         | 总是在历史列表切换          | 在对应分类列表切换（自定义/收藏/历史） |
| 低版本 M3U 导入/导出   | 显示灰色不可选            | 正常显示并可选              |
| Android 6.0 兼容性 | Lambda/reversed 闪退 | 正常工作                 |
| 离线模式导入          | Context 生命周期崩溃     | 正常工作                 |

***

## 六、注意事项

1. **SAF 兼容模式**: 仅在 `Utils.isCompatibilityMode()` 返回 true 时使用 `*/*` MIME 类型
2. **Java 8 特性**: 任何新增代码都应避免使用 Lambda 表达式和 `Comparator.reversed()`
3. **Fragment 生命周期**: 后台线程中访问 UI 组件前必须检查 Context 是否为 null

