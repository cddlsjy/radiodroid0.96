package net.programmierecke.radiodroid2;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.programmierecke.radiodroid2.station.ItemAdapterStation;
import net.programmierecke.radiodroid2.station.DataRadioStation;

public class FragmentCustomStations extends Fragment {
    private static final String TAG = "FragmentCustomStations";

    private RecyclerView rvStations;
    private SwipeRefreshLayout swipeRefreshLayout;

    private CustomStationManager customStationManager;
    private ItemAdapterStation stationListAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_main, menu);
    }

    void onStationClick(DataRadioStation station) {
        RadioDroidApp radioDroidApp = (RadioDroidApp) getActivity().getApplication();
        Utils.showPlaySelection(radioDroidApp, station, getActivity().getSupportFragmentManager());
    }

    private void onStationClickInternal(DataRadioStation station) {
        onStationClick(station);
    }

    public void RefreshListGui() {
        if (BuildConfig.DEBUG) Log.d(TAG, "refreshing the stations list.");

        if (stationListAdapter != null && customStationManager != null) {
            stationListAdapter.updateList(null, customStationManager.getList());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        RadioDroidApp radioDroidApp = null;
        if (getActivity() != null) {
            radioDroidApp = (RadioDroidApp) getActivity().getApplication();
        }

        if (radioDroidApp == null) {
            Log.e(TAG, "Cannot get RadioDroidApp, Activity is null");
            return new View(getContext());
        }

        customStationManager = new CustomStationManager(getActivity());

        View view = inflater.inflate(R.layout.fragment_stations, container, false);
        rvStations = (RecyclerView) view.findViewById(R.id.recyclerViewStations);

        rvStations.setAdapter(null);

        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swiperefresh);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(
                    new SwipeRefreshLayout.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "onRefresh called from SwipeRefreshLayout");
                            }
                            RefreshDownloadList();
                        }
                    });
        }

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (BuildConfig.DEBUG) Log.d(TAG, "onActivityCreated");

        if (getActivity() != null && rvStations != null) {
            LinearLayoutManager llm = new LinearLayoutManager(getActivity());
            llm.setOrientation(LinearLayoutManager.VERTICAL);
            rvStations.setLayoutManager(llm);

            DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(rvStations.getContext(), llm.getOrientation());
            rvStations.addItemDecoration(dividerItemDecoration);

            stationListAdapter = new ItemAdapterStation(getActivity(), R.layout.list_item_station);
            stationListAdapter.setStationActionsListener(new ItemAdapterStation.StationActionsListener() {
                @Override
                public void onStationClick(DataRadioStation station, int pos) {
                    onStationClickInternal(station);
                }

                @Override
                public void onStationMoved(int from, int to) {
                }

                @Override
                public void onStationSwiped(DataRadioStation station) {
                }

                public void onStationLongClick(DataRadioStation station, int pos) {
                }

                @Override
                public void onStationMoveFinished() {
                }
            });
            rvStations.setAdapter(stationListAdapter);
        }

        RefreshDownloadList();
    }

    @Override
    public void onResume() {
        super.onResume();
        RefreshDownloadList();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_load) {
            ((ActivityMain) getActivity()).LoadFavourites();
            return true;
        } else if (id == R.id.action_save) {
            ((ActivityMain) getActivity()).SaveFavourites();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void RefreshDownloadList() {
        if (BuildConfig.DEBUG) Log.d(TAG, "RefreshDownloadList");

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }

        if (stationListAdapter != null && customStationManager != null) {
            stationListAdapter.updateList(null, customStationManager.getList());
        }
    }
}