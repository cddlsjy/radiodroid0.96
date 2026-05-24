# RadioDroid 代码修改详细大纲

***

## 修改概述

本修改针对 RadioDroid 0.96 版本进行了两处功能修复：

1. **问题1**: 低版本 Android (6/8) 系统上自定义电台和收藏的 m3u 文件导入/导出菜单灰色不可用
2. **问题2**: 全屏播放器上一个/下一个按钮总是在历史电台内切换，而不是在当前显示分类内切换

***

## 修改文件清单

| 序号 | 文件                                                                                                                                     | 修改类型              |
| :- | :------------------------------------------------------------------------------------------------------------------------------------- | :---------------- |
| 1  | [HistoryManager.java](file:///e:/build_workplace/radiodroid0.96/app/src/main/java/net/programmierecke/radiodroid2/HistoryManager.java) | 核心逻辑修复            |
| 2  | [ActivityMain.java](file:///e:/build_workplace/radiodroid0.96/app/src/main/java/net/programmierecke/radiodroid2/ActivityMain.java)     | 兼容模式增强            |
| 3  | [app/build.gradle](file:///e:/build_workplace/radiodroid0.96/app/build.gradle#L31-L38)                                                 | 编译配置 (临时修改Java版本) |

***

## 详细修改说明

### 🔧 修改1: HistoryManager - 保留电台 queue 引用

**问题根源分析**:

- 当电台添加到历史记录时，`super.addFront(station)` 会将 `station.queue` 设置为 `HistoryManager.this`
- 这导致从自定义/收藏列表点击播放的电台，其 `queue` 被强制覆盖为历史管理器
- 全屏播放器的 next/previous 按钮根据 `station.queue` 决定切换范围，导致总是在历史内切换

**修改位置**: [HistoryManager.java lines 25-48](file:///e:/build_workplace/radiodroid0.96/app/src/main/java/net/programmierecke/radiodroid2/HistoryManager.java#L25-L48)

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

### 🔧 修改2: ActivityMain - 兼容模式 m3u 导入/导出增强

**问题根源分析**:

- Android 6/8 设备上 `Intent.ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` 可能受限或显示灰色
- 应用已有 "Compatibility mode" 设置，但 `LoadFavourites()`/`SaveFavourites()` 未使用此开关
- `com.github.rustamg:file-dialogs:1.0` 库已集成，但调用的 API 不正确导致编译错误

#### 修改 2.1: SaveFavourites() 兼容模式支持

**位置**: [ActivityMain.java lines 997-1006](file:///e:/build_workplace/radiodroid0.96/app/src/main/java/net/programmierecke/radiodroid2/ActivityMain.java#L997-L1006)

**修改前**: 直接使用 SAF Intent\
**修改后**: 增加 `Utils.isCompatibilityMode()` 判断

```java
if (Utils.isCompatibilityMode(this)) {
    SaveFileDialog dialog = new SaveFileDialog();
    dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme);
    Bundle args = new Bundle();
    args.putString(FileDialog.EXTENSION, "m3u");
    dialog.setArguments(args);
    dialog.show(getSupportFragmentManager(), SaveFileDialog.class.getName());
    return;
}
```

#### 修改 2.2: LoadFavourites() 兼容模式支持

**位置**: [ActivityMain.java lines 1016-1026](file:///e:/build_workplace/radiodroid0.96/app/src/main/java/net/programmierecke/radiodroid2/ActivityMain.java#L1016-L1026)

```java
if (Utils.isCompatibilityMode(this)) {
    OpenFileDialog dialog = new OpenFileDialog();
    dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme);
    Bundle args = new Bundle();
    args.putString(FileDialog.EXTENSION, "m3u");
    dialog.setArguments(args);
    dialog.show(getSupportFragmentManager(), OpenFileDialog.class.getName());
    return;
}
```

#### 修改 2.3: onFileSelected() 自定义电台完整支持

**位置**: [ActivityMain.java lines 955-980](file:///e:/build_workplace/radiodroid0.96/app/src/main/java/net/programmierecke/radiodroid2/ActivityMain.java#L955-L980)

**修改前**: 只处理 `starred` (收藏) 和 `history` (历史)\
**修改后**: 增加 `custom` (自定义电台) 判断分支

```java
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
```

#### 修改 2.4: onActivityResult() SAF 保存自定义电台支持

**位置**: [ActivityMain.java lines 834-838](file:///e:/build_workplace/radiodroid0.96/app/src/main/java/net/programmierecke/radiodroid2/ActivityMain.java#L834-L838)

```java
} else if (selectedMenuItem == R.id.nav_item_custom) {
    CustomStationManager customStationManager = new CustomStationManager(ActivityMain.this);
    success = customStationManager.SaveM3UToStream(outputStream);  // 新增 SAF 流写入
} else {
```

***

## 🔧 修改3: build.gradle 编译配置

**问题**: 系统只有 Java 11，但项目配置为 Java 17\
**修改**: 临时降级编译配置以完成本地编译

**文件**: [app/build.gradle lines 31-38](file:///e:/build_workplace/radiodroid0.96/app/build.gradle#L31-L38)

```gradle
compileOptions {
    sourceCompatibility JavaVersion.VERSION_11  // 从 VERSION_17 修改
    targetCompatibility JavaVersion.VERSION_11  // 从 VERSION_17 修改
}

kotlinOptions {
    jvmTarget = '11'  // 从 '17' 修改
}
```

> **注意**: 这是**本地编译环境适配修改**，正式发布可恢复 Java 17 配置

***

## 验证说明

### 编译验证

```
BUILD SUCCESSFUL in 2m 59s
31 actionable tasks: 10 executed, 21 up-to-date
```

### ADB 安装验证

```
卸载旧版本: Success
安装新版本: Success
设备: ff0fc848
```

### 功能测试要点

| 测试项      | 操作步骤                         | 预期结果        |
| :------- | :--------------------------- | :---------- |
| 兼容模式开关   | 设置 → 开启 "Compatibility mode" | 状态保存        |
| m3u导出兼容  | 进入收藏/自定义 → 菜单 "保存/导出"        | 弹出传统文件对话框   |
| m3u导入兼容  | 进入收藏/自定义 → 菜单 "载入/导入"        | 弹出传统文件对话框   |
| 全屏切换-自定义 | 自定义播放列表内播放电台 → 全屏 → 点下一个/上一个 | 在自定义列表内顺序切换 |
| 全屏切换-收藏  | 收藏列表内播放电台 → 全屏 → 点下一个/上一个    | 在收藏列表内顺序切换  |
| 全屏切换-历史  | 历史记录内播放电台 → 全屏 → 点下一个/上一个    | 在历史列表内顺序切换  |

***

**修改完成日期**: 2026-05-21\
**APK输出路径**: [RadioDroid-free-debug-DEV-0.96.apk](file:///E:/build_workplace/radiodroid0.96/app/build/outputs/apk/free/debug/RadioDroid-free-debug-DEV-0.96-No%20commit%20hash.apk)
