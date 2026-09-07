package com.ats.audioplayer;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ats.audioplayer.databinding.LayoutItemSongBinding;
import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);
    }

    private List<Song> songList;
    private final OnSongClickListener listener;

    public SongAdapter(List<Song> songList, OnSongClickListener listener) {
        this.songList = songList != null ? songList : new ArrayList<>();
        this.listener = listener;
    }

    public void updateList(List<Song> newList) {
        this.songList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutItemSongBinding binding = LayoutItemSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new SongViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songList.get(position);
        holder.bind(song, listener);
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        private final LayoutItemSongBinding binding;

        public SongViewHolder(@NonNull LayoutItemSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final Song song, final OnSongClickListener listener) {
            binding.title.setText(song.getTitle());
            binding.artist.setText(song.getArtist());
            binding.duration.setText(song.getFormattedDuration());
            binding.txtFormat.setText(song.getFormatBadge());

            binding.cardSong.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(song, getAdapterPosition());
                }
            });
        }
    }
}