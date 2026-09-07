package com.ats.audioplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ats.audioplayer.databinding.LayoutItemMdrcBandBinding;
import java.util.Locale;

/**
 * MDRCAdapter: Maneja los 5 bloques del compresor multibanda (MDRC / MBC ATS-2835P).
 * Bandas: SUB BASS, LOW, MID, HIGH MID, HIGH.
 * Permite ajustar en vivo: Cutoff, Gain, Threshold, Ratio, Attack, Release, Knee y Switch Enable.
 * Notifica los cambios a MainActivity.setMDRCParam(band, param, value).
 */
public class MDRCAdapter extends RecyclerView.Adapter<MDRCAdapter.MdrcViewHolder> {

    public static final String[] BAND_NAMES = {"SUB BASS", "LOW", "MID", "HIGH MID", "HIGH"};

    private final Context context;
    private final float[] cutoffs;
    private final float[] gains;
    private final float[] thresholds;
    private final float[] ratios;
    private final float[] attacks;
    private final float[] releases;
    private final float[] knees;
    private final boolean[] enabled;

    public MDRCAdapter(Context context,
                       float[] cutoffs,
                       float[] gains,
                       float[] thresholds,
                       float[] ratios,
                       float[] attacks,
                       float[] releases,
                       float[] knees,
                       boolean[] enabled) {
        this.context = context;
        this.cutoffs = cutoffs;
        this.gains = gains;
        this.thresholds = thresholds;
        this.ratios = ratios;
        this.attacks = attacks;
        this.releases = releases;
        this.knees = knees;
        this.enabled = enabled;
    }

    @NonNull
    @Override
    public MdrcViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutItemMdrcBandBinding binding = LayoutItemMdrcBandBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new MdrcViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MdrcViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return BAND_NAMES.length;
    }

    public class MdrcViewHolder extends RecyclerView.ViewHolder {
        private final LayoutItemMdrcBandBinding binding;

        public MdrcViewHolder(@NonNull LayoutItemMdrcBandBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final int band) {
            binding.bandName.setText(BAND_NAMES[band]);

            // Switch Enable
            binding.swEnable.setOnCheckedChangeListener(null);
            binding.swEnable.setChecked(enabled[band]);
            binding.swEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                enabled[band] = isChecked;
                if (context instanceof MainActivity) {
                    ((MainActivity) context).setMDRCParam(band, "enabled", isChecked ? 1.0f : 0.0f);
                }
            });

            // 1. Cutoff (20 a 20000 Hz)
            setupCutoff(band);

            // 2. Gain (-6 a +6 dB)
            setupGain(band);

            // 3. Threshold (-60 a 0 dB)
            setupThreshold(band);

            // 4. Ratio (1.0 a 10.0)
            setupRatio(band);

            // 5. Attack (1 a 100 ms)
            setupAttack(band);

            // 6. Release (20 a 500 ms)
            setupRelease(band);

            // 7. Knee (0 a 12 dB)
            setupKnee(band);

            // Gain Reduction Meter
            float currentThresh = thresholds[band];
            int grPercent = Math.min(100, Math.max(5, (int) (Math.abs(currentThresh) * 1.5f)));
            binding.gainReduction.setProgress(grPercent);
            float grDb = (grPercent / 100.0f) * 8.0f;
            binding.txtGainReductionVal.setText(String.format(Locale.US, "-%.1f dB", grDb));
        }

        private void setupCutoff(final int band) {
            float cutoff = cutoffs[band];
            binding.lblCutoff.setText(String.format(Locale.US, "Cutoff: %d Hz", (int) cutoff));
            int progress = (int) (Math.log10(cutoff / 20.0) / Math.log10(1000.0) * 100.0);
            binding.skCutoff.setProgress(Math.max(0, Math.min(100, progress)));

            binding.skCutoff.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float freq = (float) (20.0 * Math.pow(1000.0, progress / 100.0));
                        cutoffs[band] = freq;
                        binding.lblCutoff.setText(String.format(Locale.US, "Cutoff: %d Hz", (int) freq));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setMDRCParam(band, "cutoff", freq);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        private void setupGain(final int band) {
            float gain = gains[band];
            binding.lblGain.setText(String.format(Locale.US, "Gain: %+.1f dB", gain));
            int progress = Math.round((gain + 6.0f) * 10.0f);
            binding.skGain.setProgress(Math.max(0, Math.min(120, progress)));

            binding.skGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float val = (progress / 10.0f) - 6.0f;
                        gains[band] = val;
                        binding.lblGain.setText(String.format(Locale.US, "Gain: %+.1f dB", val));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setMDRCParam(band, "gain", val);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        private void setupThreshold(final int band) {
            float thresh = thresholds[band];
            binding.lblThreshold.setText(String.format(Locale.US, "Thresh: %.1f dB", thresh));
            int progress = Math.round(thresh + 60.0f);
            binding.skThreshold.setProgress(Math.max(0, Math.min(60, progress)));

            binding.skThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float val = progress - 60.0f;
                        thresholds[band] = val;
                        binding.lblThreshold.setText(String.format(Locale.US, "Thresh: %.1f dB", val));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setMDRCParam(band, "threshold", val);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        private void setupRatio(final int band) {
            float ratio = ratios[band];
            binding.lblRatio.setText(String.format(Locale.US, "Ratio: %.1f : 1", ratio));
            int progress = Math.round((ratio - 1.0f) * 10.0f);
            binding.skRatio.setProgress(Math.max(0, Math.min(90, progress)));

            binding.skRatio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float val = 1.0f + (progress / 10.0f);
                        ratios[band] = val;
                        binding.lblRatio.setText(String.format(Locale.US, "Ratio: %.1f : 1", val));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setMDRCParam(band, "ratio", val);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        private void setupAttack(final int band) {
            float attack = attacks[band];
            binding.lblAttack.setText(String.format(Locale.US, "Attack: %d ms", (int) attack));
            int progress = Math.round(attack - 1.0f);
            binding.skAttack.setProgress(Math.max(0, Math.min(99, progress)));

            binding.skAttack.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float val = progress + 1.0f;
                        attacks[band] = val;
                        binding.lblAttack.setText(String.format(Locale.US, "Attack: %d ms", (int) val));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setMDRCParam(band, "attack", val);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        private void setupRelease(final int band) {
            float release = releases[band];
            binding.lblRelease.setText(String.format(Locale.US, "Release: %d ms", (int) release));
            int progress = Math.round(release - 20.0f);
            binding.skRelease.setProgress(Math.max(0, Math.min(480, progress)));

            binding.skRelease.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float val = progress + 20.0f;
                        releases[band] = val;
                        binding.lblRelease.setText(String.format(Locale.US, "Release: %d ms", (int) val));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setMDRCParam(band, "release", val);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        private void setupKnee(final int band) {
            float knee = knees[band];
            binding.lblKnee.setText(String.format(Locale.US, "Knee: %.1f dB", knee));
            int progress = Math.round(knee * 10.0f);
            binding.skKnee.setProgress(Math.max(0, Math.min(120, progress)));

            binding.skKnee.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float val = progress / 10.0f;
                        knees[band] = val;
                        binding.lblKnee.setText(String.format(Locale.US, "Knee: %.1f dB", val));
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).setMDRCParam(band, "knee", val);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }
}