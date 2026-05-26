package net.programmierecke.radiodroid2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.paging.PagedList;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import net.programmierecke.radiodroid2.history.TrackHistoryAdapter;
import net.programmierecke.radiodroid2.history.TrackHistoryEntry;
import net.programmierecke.radiodroid2.history.TrackHistoryRepository;
import net.programmierecke.radiodroid2.history.TrackHistoryViewModel;
import net.programmierecke.radiodroid2.recording.Recordable;
import net.programmierecke.radiodroid2.recording.RecordingsAdapter;
import net.programmierecke.radiodroid2.recording.RecordingsManager;
import net.programmierecke.radiodroid2.recording.RunningRecordingInfo;
import net.programmierecke.radiodroid2.service.PauseReason;
import net.programmierecke.radiodroid2.service.PlayerService;
import net.programmierecke.radiodroid2.service.PlayerServiceUtil;
import net.programmierecke.radiodroid2.station.DataRadioStation;
import net.programmierecke.radiodroid2.station.StationActions;
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo;
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo;
import net.programmierecke.radiodroid2.station.live.metadata.TrackMetadata;
import net.programmierecke.radiodroid2.station.live.metadata.TrackMetadataCallback;
import net.programmierecke.radiodroid2.station.live.metadata.TrackMetadataSearcher;
import net.programmierecke.radiodroid2.utils.RefreshHandler;
import net.programmierecke.radiodroid2.views.RecyclerAwareNestedScrollView;
import net.programmierecke.radiodroid2.views.TagsView;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;

public class FragmentPlayerFull extends Fragment {
    private final String TAG = "FragmentPlayerFull";

    private static final String FULLSCREEN_MODE_DEFAULT = "default";
    private static final String FULLSCREEN_MODE_SIMPLE = "simple";

    private final static int PERM_REQ_STORAGE_RECORD = 1001;

    public interface TouchInterceptListener {
        void requestDisallowInterceptTouchEvent(boolean disallow);
    }

    private TouchInterceptListener touchInterceptListener;

    private BroadcastReceiver updateUIReceiver;

    private boolean initialized = false;

    private RefreshHandler refreshHandler = new RefreshHandler();
    private TimedUpdateTask timedUpdateTask = new TimedUpdateTask(this);
    private static final int TIMED_UPDATE_INTERVAL = 1000;

    private PlayerTrackMetadataCallback trackMetadataCallback;
    private TrackMetadataCallback.FailureType trackMetadataLastFailureType = null;
    private StreamLiveInfo lastLiveInfoForTrackMetadata = null;

    private RecordingsManager recordingsManager;
    private java.util.Observer recordingsObserver;

    private FavouriteManager favouriteManager;
    private FavouritesObserver favouritesObserver = new FavouritesObserver();

    private TrackHistoryRepository trackHistoryRepository;
    private TrackHistoryAdapter trackHistoryAdapter;

    private RecordingsAdapter recordingsAdapter;

    private boolean storagePermissionsDenied = false;

    private RecyclerAwareNestedScrollView scrollViewContent;

    private ViewPager pagerArtAndInfo;
    private ArtAndInfoPagerAdapter artAndInfoPagerAdapter;

    private TextView textViewGeneralInfo;
    private TextView textViewTimePlayed;
    private TextView textViewNetworkUsageInfo;
    private TextView textViewTimeCached;

    private Group groupRecordings;
    private ImageView imgRecordingIcon;
    private TextView textViewRecordingSize;
    private TextView textViewRecordingName;

    private ViewPager pagerHistoryAndRecordings;
    private HistoryAndRecordsPagerAdapter historyAndRecordsPagerAdapter;

    private TrackHistoryViewModel trackHistoryViewModel;

    private ImageButton btnPlay;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnRecord;
    private ImageButton btnFavourite;

    private boolean isSimpleMode = false;
    private String currentInflatedMode = FULLSCREEN_MODE_DEFAULT;
    private String currentInflatedBgColor = "white";
    private int currentInflatedOrientation = -1;
    private boolean currentInflatedAutoRotate = false;

    private com.google.android.material.imageview.ShapeableImageView simpleStationIcon;
    private TextView simpleStationName;
    private TextView simpleStationMetadata;
    private ImageButton simpleBtnFullscreenExit;

    private FrameLayout contentContainer;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RadioDroidApp radioDroidApp = (RadioDroidApp) requireActivity().getApplication();

        recordingsManager = radioDroidApp.getRecordingsManager();
        recordingsObserver = (observable, o) -> updateRecordings();

        favouriteManager = radioDroidApp.getFavouriteManager();

        trackHistoryAdapter = new TrackHistoryAdapter(requireActivity());

        trackHistoryRepository = radioDroidApp.getTrackHistoryRepository();

        updateUIReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case PlayerService.PLAYER_SERVICE_STATE_CHANGE: {
                        fullUpdate();
                        break;
                    }
                    case PlayerService.PLAYER_SERVICE_META_UPDATE: {
                        fullUpdate();
                        break;
                    }
                }
            }
        };

        contentContainer = new FrameLayout(requireContext());
        contentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        isSimpleMode = FULLSCREEN_MODE_SIMPLE.equals(getEffectiveMode());

        inflateContent(contentContainer);

        return contentContainer;
    }

    private String getEffectiveMode() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean autoRotate = prefs.getBoolean("fullscreen_auto_rotate", false);
        if (autoRotate) {
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                return FULLSCREEN_MODE_SIMPLE;
            } else {
                return FULLSCREEN_MODE_DEFAULT;
            }
        }
        return prefs.getString("fullscreen_mode", FULLSCREEN_MODE_DEFAULT);
    }

    private void inflateContent(FrameLayout container) {
        container.removeAllViews();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String fullscreenMode = getEffectiveMode();
        isSimpleMode = FULLSCREEN_MODE_SIMPLE.equals(fullscreenMode);
        String bgColor = prefs.getString("fullscreen_bg_color", "white");

        currentInflatedMode = fullscreenMode;
        currentInflatedBgColor = bgColor;
        currentInflatedOrientation = getResources().getConfiguration().orientation;
        currentInflatedAutoRotate = prefs.getBoolean("fullscreen_auto_rotate", false);

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        if (isSimpleMode) {
            int orientation = getResources().getConfiguration().orientation;
            boolean isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            boolean isDarkBg = "dark_blue".equals(bgColor);

            int layoutRes;
            if (isLandscape) {
                layoutRes = isDarkBg ? R.layout.layout_player_full_simple_landscape_dark : R.layout.layout_player_full_simple_landscape;
            } else {
                layoutRes = isDarkBg ? R.layout.layout_player_full_simple_portrait_dark : R.layout.layout_player_full_simple_portrait;
            }

            View view = inflater.inflate(layoutRes, container, true);

            simpleStationIcon = view.findViewById(R.id.stationIcon);
            simpleStationName = view.findViewById(R.id.playerStationName);
            simpleStationMetadata = view.findViewById(R.id.playerStationMetadata);
            simpleBtnFullscreenExit = view.findViewById(R.id.buttonFullscreenExit);
            btnPlay = view.findViewById(R.id.buttonPlay);
            btnPrev = view.findViewById(R.id.buttonPrev);
            btnNext = view.findViewById(R.id.buttonNext);
            btnRecord = null;
            btnFavourite = null;

            scrollViewContent = null;
            pagerArtAndInfo = null;
            artAndInfoPagerAdapter = null;
            textViewGeneralInfo = null;
            textViewTimePlayed = null;
            textViewNetworkUsageInfo = null;
            textViewTimeCached = null;
            groupRecordings = null;
            pagerHistoryAndRecordings = null;
            historyAndRecordsPagerAdapter = null;

            if (simpleBtnFullscreenExit != null) {
                simpleBtnFullscreenExit.setOnClickListener(v -> {
                    requireActivity().onBackPressed();
                });
            }
        } else {
            View view = inflater.inflate(R.layout.layout_player_full, container, true);

            scrollViewContent = view.findViewById(R.id.scrollViewContent);

            pagerArtAndInfo = view.findViewById(R.id.pagerArtAndInfo);
            artAndInfoPagerAdapter = new ArtAndInfoPagerAdapter(requireContext(), pagerArtAndInfo);
            pagerArtAndInfo.setAdapter(artAndInfoPagerAdapter);

            pagerArtAndInfo.setOnTouchListener(new View.OnTouchListener() {
                private static final int DRAG_THRESHOLD = 30;
                private int downX;
                private int downY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = (int) event.getRawX();
                            downY = (int) event.getRawY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            int distanceX = Math.abs((int) event.getRawX() - downX);
                            int distanceY = Math.abs((int) event.getRawY() - downY);

                            if (distanceX > distanceY && distanceX > DRAG_THRESHOLD) {
                                pagerArtAndInfo.getParent().requestDisallowInterceptTouchEvent(true);
                                scrollViewContent.getParent().requestDisallowInterceptTouchEvent(false);
                                if (touchInterceptListener != null) {
                                    touchInterceptListener.requestDisallowInterceptTouchEvent(true);
                                }
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            scrollViewContent.getParent().requestDisallowInterceptTouchEvent(false);
                            pagerArtAndInfo.getParent().requestDisallowInterceptTouchEvent(false);
                            if (touchInterceptListener != null) {
                                touchInterceptListener.requestDisallowInterceptTouchEvent(false);
                            }
                            break;
                    }
                    return false;
                }
            });

            textViewGeneralInfo = view.findViewById(R.id.textViewGeneralInfo);
            textViewTimePlayed = view.findViewById(R.id.textViewTimePlayed);
            textViewNetworkUsageInfo = view.findViewById(R.id.textViewNetworkUsageInfo);
            textViewTimeCached = view.findViewById(R.id.textViewTimeCached);

            groupRecordings = view.findViewById(R.id.group_recording_info);
            imgRecordingIcon = view.findViewById(R.id.imgRecordingIcon);
            textViewRecordingSize = view.findViewById(R.id.textViewRecordingSize);
            textViewRecordingName = view.findViewById(R.id.textViewRecordingName);

            pagerHistoryAndRecordings = view.findViewById(R.id.pagerHistoryAndRecordings);
            historyAndRecordsPagerAdapter = new HistoryAndRecordsPagerAdapter(requireContext(), pagerHistoryAndRecordings);
            pagerHistoryAndRecordings.setAdapter(historyAndRecordsPagerAdapter);

            btnPlay = view.findViewById(R.id.buttonPlay);
            btnPrev = view.findViewById(R.id.buttonPrev);
            btnNext = view.findViewById(R.id.buttonNext);
            btnRecord = view.findViewById(R.id.buttonRecord);
            btnFavourite = view.findViewById(R.id.buttonFavorite);

            historyAndRecordsPagerAdapter.recyclerViewSongHistory.setAdapter(trackHistoryAdapter);
            trackHistoryAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    final LinearLayoutManager lm = (LinearLayoutManager) historyAndRecordsPagerAdapter.recyclerViewSongHistory.getLayoutManager();
                    if (lm.findFirstVisibleItemPosition() < 2) {
                        historyAndRecordsPagerAdapter.recyclerViewSongHistory.scrollToPosition(0);
                    }
                }
            });

            LinearLayoutManager llmHistory = new LinearLayoutManager(getContext());
            llmHistory.setOrientation(RecyclerView.VERTICAL);
            historyAndRecordsPagerAdapter.recyclerViewSongHistory.setLayoutManager(llmHistory);

            DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(historyAndRecordsPagerAdapter.recyclerViewSongHistory.getContext(), llmHistory.getOrientation());
            historyAndRecordsPagerAdapter.recyclerViewSongHistory.addItemDecoration(dividerItemDecoration);

            trackHistoryViewModel = ViewModelProviders.of(this).get(TrackHistoryViewModel.class);
            trackHistoryViewModel.getAllHistoryPaged().observe(getViewLifecycleOwner(), new Observer<PagedList<TrackHistoryEntry>>() {
                @Override
                public void onChanged(@Nullable PagedList<TrackHistoryEntry> songHistoryEntries) {
                    trackHistoryAdapter.submitList(songHistoryEntries);
                }
            });

            recordingsAdapter = new RecordingsAdapter(requireContext(), recordingsManager);
            recordingsAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    final LinearLayoutManager lm = (LinearLayoutManager) historyAndRecordsPagerAdapter.recyclerViewRecordings.getLayoutManager();
                    if (lm.findFirstVisibleItemPosition() < 2) {
                        historyAndRecordsPagerAdapter.recyclerViewRecordings.scrollToPosition(0);
                    }
                }
            });

            historyAndRecordsPagerAdapter.recyclerViewRecordings.setAdapter(recordingsAdapter);

            LinearLayoutManager llmRecordings = new LinearLayoutManager(getContext());
            llmRecordings.setOrientation(RecyclerView.VERTICAL);
            historyAndRecordsPagerAdapter.recyclerViewRecordings.setLayoutManager(llmRecordings);

            historyAndRecordsPagerAdapter.recyclerViewRecordings.addItemDecoration(dividerItemDecoration);

            ViewTreeObserver viewTreeObserver = pagerHistoryAndRecordings.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        ViewGroup.LayoutParams layoutParams = pagerHistoryAndRecordings.getLayoutParams();
                        final int newHeight = scrollViewContent.getHeight();
                        if (newHeight != layoutParams.height) {
                            layoutParams.height = newHeight;
                            pagerHistoryAndRecordings.setLayoutParams(layoutParams);
                        }
                    }
                });
            }

            simpleStationIcon = null;
            simpleStationName = null;
            simpleStationMetadata = null;
        }

        setupListeners();
    }

    public boolean handleKeyEvent(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    PlayerServiceUtil.skipToPrevious();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    PlayerServiceUtil.skipToNext();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    togglePlayPause();
                    return true;
            }
        }
        return false;
    }

    private void togglePlayPause() {
        if (PlayerServiceUtil.isPlaying()) {
            if (PlayerServiceUtil.isRecording()) {
                PlayerServiceUtil.stopRecording();
                updateRunningRecording();
            }
            PlayerServiceUtil.pause(PauseReason.USER);
        } else {
            playLastFromHistory();
        }
    }

    public void init() {
        if (rebuildViewIfNeeded()) {
            return;
        }
        if (!initialized) {
            fullUpdate();
        }
    }

    private boolean rebuildViewIfNeeded() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String currentMode = prefs.getString("fullscreen_mode", FULLSCREEN_MODE_DEFAULT);
        String currentBg = prefs.getString("fullscreen_bg_color", "white");
        boolean currentAutoRotate = prefs.getBoolean("fullscreen_auto_rotate", false);
        int currentOrientation = getResources().getConfiguration().orientation;

        boolean effectiveModeChanged;
        if (currentAutoRotate) {
            effectiveModeChanged = currentInflatedAutoRotate != currentAutoRotate
                    || currentInflatedOrientation != currentOrientation;
        } else {
            effectiveModeChanged = !currentMode.equals(currentInflatedMode)
                    || currentInflatedAutoRotate != currentAutoRotate;
        }
        boolean bgChanged = !currentBg.equals(currentInflatedBgColor);

        if (!effectiveModeChanged && !bgChanged) {
            return false;
        }

        stopUpdating();
        initialized = false;

        inflateContent(contentContainer);

        fullUpdate();
        startUpdating();
        return true;
    }

    private void setupListeners() {
        if (btnPlay == null) return;

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (PlayerServiceUtil.isPlaying()) {
                    if (PlayerServiceUtil.isRecording()) {
                        PlayerServiceUtil.stopRecording();
                        updateRunningRecording();
                    }
                    PlayerServiceUtil.pause(PauseReason.USER);
                } else {
                    playLastFromHistory();
                }
            }
        });

        if (btnPrev != null) {
            btnPrev.setOnClickListener(view -> PlayerServiceUtil.skipToPrevious());
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(view -> PlayerServiceUtil.skipToNext());
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupListeners();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        if (hidden) {
            stopUpdating();
        } else {
            if (!rebuildViewIfNeeded()) {
                startUpdating();
            }
        }

        if (touchInterceptListener != null) {
            touchInterceptListener.requestDisallowInterceptTouchEvent(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!rebuildViewIfNeeded()) {
            startUpdating();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopUpdating();
    }

    public void setTouchInterceptListener(TouchInterceptListener touchInterceptListener) {
        this.touchInterceptListener = touchInterceptListener;
    }

    private void startUpdating() {
        fullUpdate();

        refreshHandler.executePeriodically(timedUpdateTask, TIMED_UPDATE_INTERVAL);

        IntentFilter filter = new IntentFilter();

        filter.addAction(PlayerService.PLAYER_SERVICE_TIMER_UPDATE);
        filter.addAction(PlayerService.PLAYER_SERVICE_STATE_CHANGE);
        filter.addAction(PlayerService.PLAYER_SERVICE_META_UPDATE);

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateUIReceiver, filter);

        recordingsManager.getSavedRecordingsObservable().addObserver(recordingsObserver);

        favouriteManager.addObserver(favouritesObserver);
    }

    private void stopUpdating() {
        if (getView() == null) {
            return;
        }

        refreshHandler.cancel();

        if (trackMetadataCallback != null) {
            trackMetadataCallback.cancel();
        }

        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateUIReceiver);
        } catch (IllegalArgumentException e) {
            // receiver was not registered
        }

        try {
            recordingsManager.getSavedRecordingsObservable().deleteObserver(recordingsObserver);
        } catch (Exception e) {
            // ignore
        }

        try {
            favouriteManager.deleteObserver(favouritesObserver);
        } catch (Exception e) {
            // ignore
        }
    }

    public void resetScroll() {
        if (isSimpleMode || scrollViewContent == null) {
            return;
        }
        scrollViewContent.scrollTo(0, 0);
        historyAndRecordsPagerAdapter.recyclerViewSongHistory.scrollToPosition(0);
        historyAndRecordsPagerAdapter.recyclerViewRecordings.scrollToPosition(0);
    }

    public boolean isScrolled() {
        if (isSimpleMode || scrollViewContent == null) {
            return false;
        }
        return scrollViewContent.getScrollY() > 0;
    }

    private void playLastFromHistory() {
        RadioDroidApp radioDroidApp = (RadioDroidApp) requireActivity().getApplication();
        DataRadioStation station = PlayerServiceUtil.getCurrentStation();

        if (station == null) {
            HistoryManager historyManager = radioDroidApp.getHistoryManager();
            station = historyManager.getFirst();
        }

        if (station != null) {
            Utils.showPlaySelection(radioDroidApp, station, getActivity().getSupportFragmentManager());
        }
    }

    private void fullUpdate() {
        DataRadioStation station = Utils.getCurrentOrLastStation(requireContext());

        if (station != null) {
            final ShoutcastInfo shoutcastInfo = PlayerServiceUtil.getShoutcastInfo();

            final StreamLiveInfo liveInfo = PlayerServiceUtil.getMetadataLive();
            String streamTitle = liveInfo.getTitle();

            String displayText = "";
            String displayTextForAccessibility = "";

            String artistDisplay = liveInfo.getArtist();
            String trackDisplay = liveInfo.getTrack();

            if ("Unknown Artist".equals(artistDisplay) || "未知".equals(artistDisplay)) {
                artistDisplay = getString(R.string.unknown_artist);
            }
            if ("Unknown Track".equals(trackDisplay) || "未知".equals(trackDisplay)) {
                trackDisplay = getString(R.string.unknown_track);
            }

            if (liveInfo.hasArtistAndTrack()) {
                displayText = artistDisplay + " - " + trackDisplay;
                displayTextForAccessibility = getString(R.string.now_playing_accessibility, trackDisplay, artistDisplay);

                if (("Unknown Artist".equals(liveInfo.getArtist()) || "未知".equals(liveInfo.getArtist())) &&
                        !("Unknown Track".equals(liveInfo.getTrack()) || "未知".equals(liveInfo.getTrack()))) {
                    displayTextForAccessibility = getString(R.string.now_playing_artist_unknown, trackDisplay);
                } else if (!("Unknown Artist".equals(liveInfo.getArtist()) || "未知".equals(liveInfo.getArtist())) &&
                        ("Unknown Track".equals(liveInfo.getTrack()) || "未知".equals(liveInfo.getTrack()))) {
                    displayTextForAccessibility = getString(R.string.now_playing_track_unknown, artistDisplay);
                } else if (("Unknown Artist".equals(liveInfo.getArtist()) || "未知".equals(liveInfo.getArtist())) &&
                        ("Unknown Track".equals(liveInfo.getTrack()) || "未知".equals(liveInfo.getTrack()))) {
                    displayTextForAccessibility = getString(R.string.now_playing_all_unknown);
                }
            } else if (!TextUtils.isEmpty(streamTitle)) {
                displayText = streamTitle;
                displayTextForAccessibility = getString(R.string.now_playing_stream, streamTitle);
            }

            if (isSimpleMode) {
                if (simpleStationName != null) {
                    simpleStationName.setText(station.Name);
                }
                if (simpleStationMetadata != null) {
                    if (!TextUtils.isEmpty(displayText)) {
                        simpleStationMetadata.setText(displayText);
                    } else {
                        simpleStationMetadata.setText(station.Name);
                    }
                }
            } else {
                if (textViewGeneralInfo != null) {
                    if (!TextUtils.isEmpty(displayText)) {
                        textViewGeneralInfo.setText(displayText);
                        textViewGeneralInfo.setContentDescription(displayTextForAccessibility);
                    } else {
                        textViewGeneralInfo.setText(station.Name);
                        textViewGeneralInfo.setContentDescription(getString(R.string.content_desc_listening_station, station.Name));
                    }
                }

                if (artAndInfoPagerAdapter != null) {
                    Drawable flag = CountryFlagsLoader.getInstance().getFlag(requireContext(), station.CountryCode);
                    if (flag != null) {
                        float k = flag.getMinimumWidth() / (float) flag.getMinimumHeight();
                        float viewHeight = artAndInfoPagerAdapter.textViewStationDescription.getTextSize();
                        flag.setBounds(0, 0, (int) (k * viewHeight), (int) viewHeight);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        artAndInfoPagerAdapter.textViewStationDescription.setCompoundDrawablesRelative(flag, null, null, null);
                    } else {
                        artAndInfoPagerAdapter.textViewStationDescription.setCompoundDrawables(flag, null, null, null);
                    }

                    artAndInfoPagerAdapter.textViewStationDescription.setText(station.getLongDetails(requireContext()));

                    String[] tags = station.TagsAll.split(",");
                    artAndInfoPagerAdapter.viewTags.setTags(Arrays.asList(tags));
                }
            }
        }

        updateAlbumArt();
        if (!isSimpleMode) {
            updateRecordings();
        }

        if (btnPlay != null) {
            if (PlayerServiceUtil.isPlaying()) {
                btnPlay.setImageResource(R.drawable.ic_pause_24dp);
                btnPlay.setContentDescription(getResources().getString(R.string.detail_pause));
            } else {
                btnPlay.setImageResource(R.drawable.ic_play_arrow_24dp);
                btnPlay.setContentDescription(getResources().getString(R.string.detail_play));
            }
        }

        if (!isSimpleMode) {
            updateRecordButton(PlayerServiceUtil.isPlaying(), PlayerServiceUtil.isRecording());
            updateFavouriteButton();
        }

        timedUpdateTask.run();

        initialized = true;
    }

    private void updatePlaybackButtons(boolean playing, boolean recording) {
        updatePlayButton(playing);
        updateRecordButton(playing, recording);
    }

    private void updatePlayButton(boolean playing) {
        if (btnPlay == null) return;

        if (playing) {
            btnPlay.setImageResource(R.drawable.ic_pause_24dp);
            btnPlay.setContentDescription(getResources().getString(R.string.detail_pause));
        } else {
            btnPlay.setImageResource(R.drawable.ic_play_arrow_24dp);
            btnPlay.setContentDescription(getResources().getString(R.string.detail_play));
        }
    }

    private void updateRecordButton(final boolean playing, final boolean recording) {
        if (btnRecord == null) {
            return;
        }
        btnRecord.setEnabled(playing);

        if (recording) {
            btnRecord.setImageResource(R.drawable.ic_stop_recording);
            btnRecord.setContentDescription(getResources().getString(R.string.detail_stop));
        } else {
            btnRecord.setImageResource(R.drawable.ic_start_recording);

            if (!storagePermissionsDenied) {
                btnRecord.setContentDescription(getResources().getString(R.string.image_button_record));
            } else {
                btnRecord.setContentDescription(getResources().getString(R.string.image_button_record_request_permission));
            }
        }
    }

    private void updateRecordings() {
        if (recordingsAdapter == null) {
            return;
        }
        recordingsAdapter.setRecordings(recordingsManager.getSavedRecordings());
        updateRunningRecording();
    }

    private void updateRunningRecording() {
        if (isSimpleMode || groupRecordings == null) {
            return;
        }
        if (PlayerServiceUtil.isRecording()) {
            final Map<Recordable, RunningRecordingInfo> runningRecordings = recordingsManager.getRunningRecordings();
            if (runningRecordings == null || runningRecordings.isEmpty()) {
                groupRecordings.setVisibility(View.GONE);
                imgRecordingIcon.clearAnimation();
                return;
            }
            final RunningRecordingInfo recordingInfo = runningRecordings.entrySet().iterator().next().getValue();

            groupRecordings.setVisibility(View.VISIBLE);
            imgRecordingIcon.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.blink_recording));
            textViewRecordingSize.setText(Utils.getReadableBytes(recordingInfo.getBytesWritten()));
            textViewRecordingName.setText(recordingInfo.getFileName());
        } else {
            groupRecordings.setVisibility(View.GONE);
            imgRecordingIcon.clearAnimation();
        }
    }

    private void updateAlbumArt() {
        DataRadioStation station = PlayerServiceUtil.getCurrentStation();
        if (station == null) {
            return;
        }

        final StreamLiveInfo liveInfo = PlayerServiceUtil.getMetadataLive();

        if (isSimpleMode) {
            if (simpleStationIcon != null) {
                if (station.hasIcon()) {
                    loadStationIconWithFallback(simpleStationIcon, station.IconUrl, station.HomePageUrl);
                } else if (!TextUtils.isEmpty(station.HomePageUrl)) {
                    loadStationIconWithFallback(simpleStationIcon, null, station.HomePageUrl);
                } else {
                    simpleStationIcon.setImageResource(R.drawable.ic_launcher);
                }
            }
            return;
        }

        if (artAndInfoPagerAdapter == null) {
            return;
        }

        if (lastLiveInfoForTrackMetadata != null &&
                TextUtils.equals(lastLiveInfoForTrackMetadata.getArtist(), liveInfo.getArtist()) &&
                TextUtils.equals(lastLiveInfoForTrackMetadata.getTrack(), liveInfo.getTrack()) &&
                !TrackMetadataCallback.FailureType.RECOVERABLE.equals(trackMetadataLastFailureType)) {
            return;
        }

        final RadioDroidApp radioDroidApp = (RadioDroidApp) requireActivity().getApplication();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(radioDroidApp);
        String LastFMApiKey = sharedPref.getString("last_fm_api_key", "");

        if (TextUtils.isEmpty(liveInfo.getArtist()) || TextUtils.isEmpty(liveInfo.getTrack()) ||
                LastFMApiKey.isEmpty()) {
            if (artAndInfoPagerAdapter != null) {
                if (station.hasIcon()) {
                    loadStationIconWithFallback(artAndInfoPagerAdapter.imageViewArt, station.IconUrl, station.HomePageUrl);
                } else if (!TextUtils.isEmpty(station.HomePageUrl)) {
                    loadStationIconWithFallback(artAndInfoPagerAdapter.imageViewArt, null, station.HomePageUrl);
                } else {
                    artAndInfoPagerAdapter.imageViewArt.setImageResource(R.drawable.ic_launcher);
                }
            }
            return;
        }

        trackMetadataLastFailureType = null;
        lastLiveInfoForTrackMetadata = liveInfo;

        if (trackMetadataCallback != null) {
            trackMetadataCallback.cancel();
        }

        TrackMetadataSearcher trackMetadataSearcher = radioDroidApp.getTrackMetadataSearcher();

        final WeakReference<FragmentPlayerFull> fragmentWeakReference = new WeakReference<>(this);
        trackHistoryRepository.getLastInsertedHistoryItem((trackHistoryEntry, dao) -> {
            if (trackHistoryEntry == null) {
                Log.e(TAG, "trackHistoryEntry is null in updateAlbumArt which should not happen.");
                return;
            }

            if (!TextUtils.isEmpty(trackHistoryEntry.artUrl)) {
                return;
            }

            FragmentPlayerFull fragment = fragmentWeakReference.get();
            if (fragment != null) {
                fragment.requireActivity().runOnUiThread(() -> {
                    if (fragment.isResumed()) {
                        fragment.trackMetadataCallback = new PlayerTrackMetadataCallback(fragmentWeakReference, trackHistoryEntry);
                        trackMetadataSearcher.fetchTrackMetadata(LastFMApiKey, liveInfo.getArtist(), liveInfo.getTrack(), fragment.trackMetadataCallback);
                    }
                });
            }
        });
    }

    private void updateFavouriteButton() {
        DataRadioStation station = Utils.getCurrentOrLastStation(requireContext());

        if (btnFavourite == null) {
            return;
        }

        if (station != null && favouriteManager.has(station.StationUuid)) {
            btnFavourite.setImageResource(R.drawable.ic_star_24dp);
            btnFavourite.setContentDescription(requireContext().getApplicationContext().getString(R.string.detail_unstar));
        } else {
            btnFavourite.setImageResource(R.drawable.ic_star_border_24dp);
            btnFavourite.setContentDescription(requireContext().getApplicationContext().getString(R.string.detail_star));
        }
    }

    private class FavouritesObserver implements java.util.Observer {

        @Override
        public void update(Observable o, Object arg) {
            updateFavouriteButton();
        }
    }

    private static class PlayerTrackMetadataCallback implements TrackMetadataCallback {
        private boolean canceled = false;
        private WeakReference<FragmentPlayerFull> fragmentWeakReference;
        private TrackHistoryEntry trackHistoryEntry;

        private PlayerTrackMetadataCallback(@NonNull WeakReference<FragmentPlayerFull> fragmentWeakReference, TrackHistoryEntry trackHistoryEntry) {
            this.fragmentWeakReference = fragmentWeakReference;
            this.trackHistoryEntry = trackHistoryEntry;
        }

        public void cancel() {
            canceled = true;
        }

        @Override
        public void onFailure(@NonNull FailureType failureType) {
            FragmentPlayerFull fragment = fragmentWeakReference.get();
            if (fragment != null) {
                fragment.requireActivity().runOnUiThread(() -> {
                    if (canceled) {
                        return;
                    }

                    fragment.trackMetadataLastFailureType = failureType;

                    if (fragment.artAndInfoPagerAdapter == null || fragment.isSimpleMode) {
                        return;
                    }

                    DataRadioStation station = Utils.getCurrentOrLastStation(fragment.requireContext());

                    if (station != null && station.hasIcon()) {
                        fragment.loadStationIconWithFallback(fragment.artAndInfoPagerAdapter.imageViewArt, station.IconUrl, station.HomePageUrl);
                    } else if (station != null && !TextUtils.isEmpty(station.HomePageUrl)) {
                        fragment.loadStationIconWithFallback(fragment.artAndInfoPagerAdapter.imageViewArt, null, station.HomePageUrl);
                    } else {
                        fragment.artAndInfoPagerAdapter.imageViewArt.setImageResource(R.drawable.ic_launcher);
                    }

                    fragment.trackMetadataCallback = null;
                });
            }
        }

        @Override
        public void onSuccess(@NonNull final TrackMetadata trackMetadata) {
            FragmentPlayerFull fragment = fragmentWeakReference.get();
            if (fragment != null) {
                fragment.requireActivity().runOnUiThread(() -> {
                    if (canceled) {
                        return;
                    }

                    if (fragment.artAndInfoPagerAdapter == null || fragment.isSimpleMode) {
                        fragment.trackMetadataCallback = null;
                        return;
                    }

                    final List<TrackMetadata.AlbumArt> albumArts = trackMetadata.getAlbumArts();
                    if (!albumArts.isEmpty()) {
                        final String albumArtUrl = albumArts.get(0).url;

                        if (!TextUtils.isEmpty(albumArtUrl)) {
                            Picasso.get()
                                    .load(albumArtUrl)
                                    .into(fragment.artAndInfoPagerAdapter.imageViewArt);

                            if (!albumArtUrl.equals(trackHistoryEntry.stationIconUrl)) {
                                fragment.trackHistoryRepository.setTrackArtUrl(trackHistoryEntry.uid, albumArtUrl);
                            }

                            fragment.trackMetadataCallback = null;

                            return;
                        }
                    }

                    onFailure(FailureType.UNRECOVERABLE);
                });
            }
        }
    }

    private class ArtAndInfoPagerAdapter extends PagerAdapter {
        private ViewGroup layoutAlbumArt;
        private ViewGroup layoutStationInfo;

        private String[] titles;

        ImageView imageViewArt;
        TextView textViewStationDescription;
        TagsView viewTags;

        ArtAndInfoPagerAdapter(@NonNull Context context, @NonNull ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);

            layoutAlbumArt = (ViewGroup) inflater.inflate(R.layout.page_player_album_art, parent, false);
            layoutStationInfo = (ViewGroup) inflater.inflate(R.layout.page_player_station_info, parent, false);

            titles = new String[]{getResources().getString(R.string.tab_player_art), getResources().getString(R.string.tab_player_info)};

            imageViewArt = layoutAlbumArt.findViewById(R.id.imageViewArt);

            textViewStationDescription = layoutStationInfo.findViewById(R.id.textViewStationDescription);
            viewTags = layoutStationInfo.findViewById(R.id.viewTags);
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            if (position == 0) {
                collection.addView(layoutAlbumArt);
                return layoutAlbumArt;
            } else {
                collection.addView(layoutStationInfo);
                return layoutStationInfo;
            }
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object view) {
            container.removeView((View) view);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }

    private class HistoryAndRecordsPagerAdapter extends PagerAdapter {
        private ViewGroup layoutSongHistory;
        private ViewGroup layoutRecordings;

        private String[] titles;

        RecyclerView recyclerViewSongHistory;
        RecyclerView recyclerViewRecordings;

        HistoryAndRecordsPagerAdapter(@NonNull Context context, @NonNull ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);

            layoutSongHistory = (ViewGroup) inflater.inflate(R.layout.page_player_history, parent, false);
            layoutRecordings = (ViewGroup) inflater.inflate(R.layout.page_player_recordings, parent, false);

            titles = new String[]{getResources().getString(R.string.tab_player_history), getResources().getString(R.string.tab_player_recordings)};

            recyclerViewSongHistory = layoutSongHistory.findViewById(R.id.recyclerViewSongHistory);
            recyclerViewRecordings = layoutRecordings.findViewById(R.id.recyclerViewRecordings);
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            if (position == 0) {
                collection.addView(layoutSongHistory);
                return layoutSongHistory;
            } else {
                collection.addView(layoutRecordings);
                return layoutRecordings;
            }
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object view) {
            container.removeView((View) view);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }

    private static class TimedUpdateTask extends RefreshHandler.ObjectBoundRunnable<FragmentPlayerFull> {
        TimedUpdateTask(FragmentPlayerFull obj) {
            super(obj);
        }

        @Override
        protected void run(FragmentPlayerFull fragmentPlayerFull) {
            final ShoutcastInfo shoutcastInfo = PlayerServiceUtil.getShoutcastInfo();

            if (PlayerServiceUtil.isPlaying()) {
                String networkUsageInfo = Utils.getReadableBytes(PlayerServiceUtil.getTransferredBytes());
                if (shoutcastInfo != null && shoutcastInfo.bitrate > 0) {
                    networkUsageInfo += " (" + shoutcastInfo.bitrate + " kbps)";
                }

                if (!fragmentPlayerFull.isSimpleMode && fragmentPlayerFull.textViewNetworkUsageInfo != null) {
                    fragmentPlayerFull.textViewNetworkUsageInfo.setText(networkUsageInfo);
                }

                final long now = System.currentTimeMillis();
                final long startTime = PlayerServiceUtil.getLastPlayStartTime();
                long deltaSeconds = startTime > 0 ? ((now - startTime) / 1000) : 0;
                deltaSeconds = Math.max(deltaSeconds, 0);

                if (!fragmentPlayerFull.isSimpleMode) {
                    if (fragmentPlayerFull.textViewTimePlayed != null) {
                        fragmentPlayerFull.textViewTimePlayed.setText(DateUtils.formatElapsedTime(deltaSeconds));
                    }
                    if (fragmentPlayerFull.textViewTimeCached != null) {
                        fragmentPlayerFull.textViewTimeCached.setText(DateUtils.formatElapsedTime(PlayerServiceUtil.getBufferedSeconds()));
                    }
                }

                fragmentPlayerFull.updateRunningRecording();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERM_REQ_STORAGE_RECORD) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                storagePermissionsDenied = false;
                PlayerServiceUtil.startRecording();
            } else {
                storagePermissionsDenied = true;
                Toast toast = Toast.makeText(getActivity(), getResources().getString(R.string.error_record_needs_write), Toast.LENGTH_SHORT);
                toast.show();
            }

            updatePlaybackButtons(PlayerServiceUtil.isPlaying(), PlayerServiceUtil.isRecording());
            updateRecordings();
        }
    }

    private void loadStationIconWithFallback(final ImageView target, final String iconUrl, final String homePageUrl) {
        final List<String> urls = new ArrayList<>();
        if (iconUrl != null && !iconUrl.trim().isEmpty()) {
            urls.add(iconUrl);
        }
        if (homePageUrl != null && !homePageUrl.trim().isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(homePageUrl);
                String domain = uri.getHost();
                if (domain != null && !domain.isEmpty()) {
                    String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
                    urls.add(scheme + "://" + domain + "/favicon.ico");
                    urls.add(scheme + "://" + domain + "/apple-touch-icon.png");
                    urls.add("https://www.google.com/s2/favicons?domain=" + domain + "&sz=128");
                }
            } catch (Exception ignored) {
            }
        }
        if (urls.isEmpty()) {
            target.setImageResource(R.drawable.ic_launcher);
            return;
        }
        tryLoadUrl(target, urls, 0);
    }

    private void tryLoadUrl(final ImageView target, final List<String> urls, final int index) {
        if (index >= urls.size()) {
            target.setImageResource(R.drawable.ic_launcher);
            return;
        }
        Picasso.get()
                .load(urls.get(index))
                .networkPolicy(index == 0 ? NetworkPolicy.OFFLINE : NetworkPolicy.NO_CACHE)
                .into(target, new Callback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(Exception e) {
                        tryLoadUrl(target, urls, index + 1);
                    }
                });
    }
}
