package net.programmierecke.radiodroid2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;


import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.programmierecke.radiodroid2.adapters.ItemAdapterStatistics;
import net.programmierecke.radiodroid2.data.DataStatistics;
import net.programmierecke.radiodroid2.database.RadioStationRepository;
import net.programmierecke.radiodroid2.interfaces.IFragmentRefreshable;

import java.util.ArrayList;
import java.util.List;

public class FragmentServerInfo extends Fragment implements IFragmentRefreshable {
    private static final String TAG = "FragmentServerInfo";
    private ItemAdapterStatistics itemAdapterStatistics;
    private RadioStationRepository repository;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_statistics,null);

        if (itemAdapterStatistics == null) {
            itemAdapterStatistics = new ItemAdapterStatistics(getActivity(), R.layout.list_item_statistic);
        }

        ListView lv = (ListView)view.findViewById(R.id.listViewStatistics);
        lv.setAdapter(itemAdapterStatistics);

        // 初始化仓库
        if (getContext() != null) {
            repository = RadioStationRepository.getInstance(getContext());
            loadLocalStatistics();
        }

        return view;
    }

    private void loadLocalStatistics() {
        if (repository == null) {
            Log.e(TAG, "Repository is null, cannot load statistics");
            return;
        }

        // 首先尝试从SharedPreferences获取服务器统计信息
        SharedPreferences sharedPref = getContext().getSharedPreferences("ServerStatistics", Context.MODE_PRIVATE);
        int serverTotal = sharedPref.getInt("stations_total", -1);
        int serverWorking = sharedPref.getInt("stations_working", -1);
        int serverBroken = sharedPref.getInt("stations_broken", -1);
        long lastUpdated = sharedPref.getLong("last_updated", 0);
        
        // 检查服务器统计信息是否有效（最近24小时内更新）
        boolean hasValidServerStats = serverTotal > 0 && serverWorking > 0 && serverBroken > 0 && 
                (System.currentTimeMillis() - lastUpdated) < 24 * 60 * 60 * 1000;
        
        if (hasValidServerStats) {
            // 使用服务器统计信息
            Log.d(TAG, "Using server statistics: " + serverTotal + " total stations, " + serverWorking + " working, " + serverBroken + " broken");
            updateStatisticsUI(serverTotal, serverWorking, serverBroken);
        } else {
            // 在后台线程获取本地数据库统计信息
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 获取所有统计数据
                        final int totalCount = repository.getStationCountSync();
                        final int workingCount = repository.getWorkingStationCountSync();
                        final int brokenCount = repository.getBrokenStationCountSync();

                        // 在主线程更新UI
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateStatisticsUI(totalCount, workingCount, brokenCount);
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading statistics", e);
                    }
                }
            }).start();
        }
    }
    
    private void updateStatisticsUI(int totalCount, int workingCount, int brokenCount) {
        // 创建统计数据
        ArrayList<DataStatistics> statistics = new ArrayList<>();
        
        // 总电台数
        DataStatistics totalStations = new DataStatistics();
        totalStations.Name = "stations_total";
        totalStations.Value = String.valueOf(totalCount);
        statistics.add(totalStations);
            
        // 工作正常的电台数
        DataStatistics workingStations = new DataStatistics();
        workingStations.Name = "stations_working";
        workingStations.Value = String.valueOf(workingCount);
        statistics.add(workingStations);
            
        // 损坏的电台数
        DataStatistics brokenStations = new DataStatistics();
        brokenStations.Name = "stations_broken";
        brokenStations.Value = String.valueOf(brokenCount);
        statistics.add(brokenStations);
            
        // 更新UI
        itemAdapterStatistics.clear();
        for (DataStatistics item : statistics) {
            itemAdapterStatistics.add(item);
        }
            
        Log.d(TAG, "Loaded statistics: " + totalCount + " total stations, " + workingCount + " working, " + brokenCount + " broken");
    }



    @Override
    public void Refresh() {
        loadLocalStatistics();
    }
}
