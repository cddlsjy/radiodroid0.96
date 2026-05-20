package net.programmierecke.radiodroid2;

/**
 * 替代过时的Observer接口，用于监听电台列表的更新
 */
public interface StationUpdateListener {
    /**
     * 当电台列表更新时调用
     */
    void onStationListUpdated();
}