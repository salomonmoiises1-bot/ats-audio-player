package com.ats.audioplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ats.audioplayer.databinding.LayoutItemEqFaderBinding;
import java.util.Locale;

/**
 * Eq32Adapter: Maneja los 32 faders de ecualización paramétrica PreEQ ATS-2835P.
 * Cada fader tiene rango de -12 dB a +12 dB con centro en 0 dB.
 * Comunica los cambios en tiempo real a MainActivity.setBandGain(position, gain).
 */
public class Eq32Adapter extends RecyclerView.Adapter<Eq32Adapter.EqViewHolder> {

    private final Context context;
    private final float[] frequencies;
    private final float[] gains;

    public Eq32Adapter(Context context, float[] frequencies, float[] gains) {
        this.context = context;
        this.frequencies = frequencies;
        this.gains = gains;
    }

    @NonNull
    @Override
    public EqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutItemEqFaderBinding binding = LayoutItemEqFaderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new EqViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EqViewHolder holder, int position) {
        float freq = frequencies[position];
        float gain = gains[position];
        holder.bind(position, freq, gain);
    }

    @Override
    public int getItemCount() {
        return frequencies.length;
    }

    public class EqViewHolder extends RecyclerView.ViewHolder {
        private final LayoutItemEqFaderBinding binding;

        public EqViewHolder(@NonNull LayoutItemEqFaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final int position, float freq, float gain) {
            binding.freqLabel.setText(formatFrequency(freq));

            // Rango: -12.0dB a +12.0dB. Max: 240. Centro: 120 (0.0 dB)
            int progress = Math.round(120f + (gain * 10f));
            binding.skBand.setProgress(progress);
            updateGainLabel(gain);

            binding.skBand.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float newGain = (progress - 120) / 10.0f;
                        gains[position] = newGain;
                        updateGainLabel(newGain);
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setBandGain(position, newGain);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            binding.btnReset.setOnClickListener(v -> {
                binding.skBand.setProgress(120);
                gains[position] = 0f;
                updateGainLabel(0f);
                if (context instanceof MainActivity) {
                    ((MainActivity) context).setBandGain(position, 0f);
                }
            });
        }

        private void updateGainLabel(float gain) {
            if (Math.abs(gain) < 0.05f) {
                binding.gainLabel.setText("0.0dB");
                binding.gainLabel.setTextColor(0xFFCCCCCC);
            } else if (gain > 0) {
                binding.gainLabel.setText(String.format(Locale.US, "+%.1fdB", gain));
                binding.gainLabel.setTextColor(0xFF00E5FF);
            } else {
                binding.gainLabel.setText(String.format(Locale.US, "%.1fdB", gain));
                binding.gainLabel.setTextColor(0xFFFF5252);
            }
        }

        private String formatFrequency(float freq) {
            if (freq >= 1000) {
                if (freq % 1000 == 0) {
                    return String.format(Locale.US, "%dk", (int) (freq / 1000));
                } else {
                    return String.format(Locale.US, "%.1fk", freq / 1000f);
                }
            } else if (freq == (int) freq) {
                return String.format(Locale.US, "%dHz", (int) freq);
            } else {
                return String.format(Locale.US, "%.1fHz", freq);
            }
        }
    }
}