package net.programmierecke.radiodroid2.station;

import androidx.recyclerview.widget.DiffUtil;
import java.util.List;

public class StationDiffCallback extends DiffUtil.Callback {

    private final List<DataRadioStation> oldList;
    private final List<DataRadioStation> newList;

    public StationDiffCallback(List<DataRadioStation> oldList, List<DataRadioStation> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList != null ? oldList.size() : 0;
    }

    @Override
    public int getNewListSize() {
        return newList != null ? newList.size() : 0;
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        DataRadioStation oldItem = oldList.get(oldItemPosition);
        DataRadioStation newItem = newList.get(newItemPosition);
        return oldItem.StationUuid.equals(newItem.StationUuid);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        DataRadioStation oldItem = oldList.get(oldItemPosition);
        DataRadioStation newItem = newList.get(newItemPosition);

        // Check essential fields that are displayed or affect logic
        if (!oldItem.Name.equals(newItem.Name)) return false;
        if (!equals(oldItem.IconUrl, newItem.IconUrl)) return false;
        if (!equals(oldItem.TagsAll, newItem.TagsAll)) return false;
        if (oldItem.Working != newItem.Working) return false;
        if (oldItem.Bitrate != newItem.Bitrate) return false;
        if (oldItem.ClickCount != newItem.ClickCount) return false;
        
        return true;
    }
    
    private boolean equals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equals(s2);
    }
}
