import io
import csv
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt
from scipy.signal import correlate, find_peaks

# === 自動偵測 matplotlib ===
try:
    import matplotlib.pyplot as plt
    from matplotlib.ticker import MultipleLocator, FuncFormatter
    import os
    from matplotlib import rcParams
    rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'Arial Unicode MS', 'sans-serif']
    rcParams['axes.unicode_minus'] = False
    HAS_PLOT = True
except ImportError:
    HAS_PLOT = False

_plot_cache = None

# === 參數設定 ===
FS_DEFAULT = 10.0
CUTOFF_DEFAULT = 1.0
ORDER = 4

DEMO_SIDE_SEC = 2.0
MIN_ACTION_DURATION = 0.3  # 最小動作時間（秒）
MAX_ACTION_DURATION = 6.0  # 最大動作時間（秒）
R_DEMO = 0.2  # 門檻 = DEMO 能量的倍數
MIN_DEMO_ENERGY = 1e-5  # DEMO 最小能量門檻

# 合併參數
BRIDGE_MAX_SEC = 0.01  # 幾乎不合併


def auto_cutoff_from_signal(t, r, fs,
                            min_period_sec=0.5,
                            max_period_sec=5.0,
                            gain_over_f0=1.5,
                            min_cut=0.25,
                            max_cut_cap=2.0):
    """根據信號週期自動估 cutoff 頻率"""
    x = np.asarray(r, dtype=float)
    if x.size < int(2 * fs):
        return CUTOFF_DEFAULT, []

    b, a = butter(4, min(1.5, 0.49 * fs) / (fs / 2), btype='low')
    x_f = filtfilt(b, a, x)
    base = moving_average(x_f, int(3 * fs))
    xd = x_f - base
    xn = (xd - np.mean(xd)) / (np.std(xd) + 1e-12)

    ac = correlate(xn, xn, mode='full')[len(xn)-1:]
    min_lag, max_lag = int(min_period_sec * fs), int(max_period_sec * fs)
    peaks, _ = find_peaks(ac[min_lag:max_lag], prominence=0.05)
    if len(peaks) == 0:
        return CUTOFF_DEFAULT, []
    lag_main = peaks[np.argmax(ac[min_lag + peaks])]
    f0_main = fs / (lag_main + min_lag)
    cutoff_main = np.clip(gain_over_f0 * f0_main, min_cut, min(max_cut_cap, 0.49*fs))

    win_size = int(5 * fs)
    step = int(2.5 * fs)
    segments, local_cuts = [], []
    for start in range(0, len(xn)-win_size, step):
        seg = xn[start:start + win_size]
        ac_seg = correlate(seg, seg, mode='full')[len(seg)-1:]
        peaks_seg, _ = find_peaks(ac_seg[min_lag:max_lag], prominence=0.05)
        if len(peaks_seg) == 0:
            continue
        lag_seg = peaks_seg[np.argmax(ac_seg[min_lag + peaks_seg])]
        f0_seg = fs / (lag_seg + min_lag)
        cutoff_seg = np.clip(gain_over_f0 * f0_seg, min_cut, min(max_cut_cap, 0.49*fs))
        t_mid = t[start + win_size//2]
        segments.append({"start": t_mid-2.5, "end": t_mid+2.5, "cutoff": float(cutoff_seg)})
        local_cuts.append(cutoff_seg)
    cutoff_final = np.median(local_cuts) if local_cuts else cutoff_main
    cutoff_final = 1.0  # 固定使用 1.0
    print(f"✅ cutoff={cutoff_final:.2f}Hz")
    return cutoff_final, segments


def calculate_fs_from_csv(file_path: str) -> float:
    """自動計算取樣率（僅用 MAINTAINING）"""
    try:
        df = pd.read_csv(file_path)
        if "state" in df.columns:
            df = df[df["state"] == "MAINTAINING"]
        if len(df) < 2 or "time_seconds" not in df.columns:
            return FS_DEFAULT
        t = pd.to_numeric(df["time_seconds"], errors="coerce").to_numpy()
        t = t[np.isfinite(t)]
        if len(t) < 2:
            return FS_DEFAULT

        sec_counts = {}
        for ti in t:
            sec = int(ti)
            sec_counts[sec] = sec_counts.get(sec, 0) + 1

        all_secs = sorted(sec_counts.keys())
        if len(all_secs) <= 2:
            stable_counts = list(sec_counts.values())
        else:
            stable_secs = all_secs[1:-1]
            stable_counts = [sec_counts[s] for s in stable_secs]

        if not stable_counts:
            return FS_DEFAULT

        min_fps = min(stable_counts)
        print(f"📊 FS = {min_fps} Hz")
        return float(min_fps)
    except Exception as e:
        print(f"⚠️ FS calculation error: {e}")
        return FS_DEFAULT


def infer_dir_from_demo(df, cols, point_name, dir_default="N"):
    """從 DEMO 段自動推斷方向（面積法）"""
    if "state" not in cols or "time_seconds" not in cols:
        return dir_default

    s_col = cols["state"]
    t_col = cols["time_seconds"]
    mask_demo = df[s_col].astype(str).str.contains("DEMO", case=False, na=False)
    if not mask_demo.any():
        print(f"⚠️ 找不到 DEMO，用預設 {dir_default}")
        return dir_default

    t_all = pd.to_numeric(df[t_col], errors="coerce").to_numpy()
    r_all = pd.to_numeric(df[cols[point_name]], errors="coerce").to_numpy()
    m_valid = np.isfinite(t_all) & np.isfinite(r_all)
    t_all, r_all = t_all[m_valid], r_all[m_valid]
    mask_demo = mask_demo.to_numpy()[m_valid]

    if np.sum(mask_demo) < 5:
        print(f"⚠️ DEMO 資料太短，用預設 {dir_default}")
        return dir_default

    t_demo = t_all[mask_demo]
    r_demo = r_all[mask_demo]
    t0, t1 = t_demo[0], t_demo[-1]

    mask_left = (t_all >= t0 - DEMO_SIDE_SEC) & (t_all < t0)
    mask_right = (t_all > t1) & (t_all <= t1 + DEMO_SIDE_SEC)

    if np.sum(mask_left) < 3 or np.sum(mask_right) < 3:
        print(f"⚠️ DEMO 前後資料不足，用預設 {dir_default}")
        return dir_default

    r_left_avg = np.mean(r_all[mask_left])
    r_right_avg = np.mean(r_all[mask_right])

    baseline = np.interp(t_demo, [t_demo[0], t_demo[-1]], [r_left_avg, r_right_avg])
    diff = r_demo - baseline

    area_pos = np.trapz(diff[diff > 0], t_demo[diff > 0]) if np.any(diff > 0) else 0
    area_neg = np.trapz(-diff[diff < 0], t_demo[diff < 0]) if np.any(diff < 0) else 0

    dir_auto = "P" if area_pos > area_neg else "N"
    print(f"📈 方向: {dir_auto} (pos={area_pos:.2e}, neg={area_neg:.2e})")
    return dir_auto


def lowpass_filter(x, fs=25.0, cutoff=0.5, order=4):
    """低通濾波器"""
    x = np.asarray(x, dtype=float)
    if x.size < 8:
        return x
    cutoff = min(cutoff, 0.49 * fs)
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    return filtfilt(b, a, x)


def moving_average(x, win_samples=20):
    """移動平均（基線估計）"""
    win_samples = max(3, min(win_samples, len(x) // 2))
    kernel = np.ones(win_samples) / win_samples
    pad_width = win_samples // 2
    x_padded = np.pad(x, pad_width, mode='edge')
    baseline_full = np.convolve(x_padded, kernel, mode='same')
    return baseline_full[pad_width:-pad_width]


def zero_crossings(x, t, deadband=0.0, min_interval=10):
    """零交叉檢測"""
    raw = []
    for i in range(1, len(x)):
        if np.isnan(x[i-1]) or np.isnan(x[i]):
            continue
        if (x[i-1] <= 0 and x[i] > 0) or (x[i-1] >= 0 and x[i] < 0):
            raw.append(i)

    crossings_all, last_keep, prev_idx = [], -min_interval, 0
    for idx in raw:
        seg_amp = np.max(np.abs(x[prev_idx:idx+1]))
        if seg_amp >= deadband and (idx - last_keep) >= min_interval:
            crossings_all.append(idx)
            last_keep = idx
        prev_idx = idx
    return crossings_all


def energy_density_interval_dir(x, t, fs, t0, t1, dir_eff):
    """計算指定時間區間內的能量密度（考慮方向）"""
    mask = (t >= t0) & (t <= t1)
    if not np.any(mask):
        return 0.0
    seg = x[mask]
    
    if dir_eff == "N":
        vals = -seg[seg < 0]
    else:
        vals = seg[seg > 0]
    
    if vals.size == 0:
        return 0.0
    
    total = np.sum(vals)
    dur = vals.size / fs
    return float(total / max(dur, 1e-9))


def compute_demo_energy_from_baseline(t_all, r_all, mask_demo, r_all_detrend, fs, dir_eff):
    """用全段統一的 detrend 計算 DEMO 能量"""
    if mask_demo is None or mask_demo.sum() < 3:
        print("⚠️ 無 DEMO 資料或 DEMO 太短 → 回傳 0")
        return 0.0

    idx_demo = np.flatnonzero(mask_demo)
    if idx_demo.size < 2:
        print("⚠️ DEMO 資料點不足 → 回傳 0")
        return 0.0

    t_demo_start = float(t_all[idx_demo[0]])
    t_demo_end = float(t_all[idx_demo[-1]])

    t_ref_start = t_demo_start - DEMO_SIDE_SEC
    t_ref_end = t_demo_start

    mask_ref = (t_all >= t_ref_start) & (t_all < t_ref_end)
    
    if np.sum(mask_ref) < int(fs * 0.5):
        print("⚠️ DEMO 前資料不足")
        return 0.0

    r_ref_det = r_all_detrend[mask_ref]
    baseline_ref = np.mean(r_ref_det)

    mask_demo_t = (t_all >= t_demo_start) & (t_all <= t_demo_end)
    t_demo = t_all[mask_demo_t]
    r_demo_det = r_all_detrend[mask_demo_t] - baseline_ref

    E_demo = energy_density_interval_dir(r_demo_det, t_demo, fs, t_demo_start, t_demo_end, dir_eff)

    if E_demo < MIN_DEMO_ENERGY:
        print(f"❌ DEMO 品質極差（能量 = {E_demo:.2e} < {MIN_DEMO_ENERGY:.2e}）")
        print("   → 建議：重新錄製 DEMO，確保做出明確動作")
    elif E_demo < MIN_DEMO_ENERGY * 10:
        print(f"⚠️ DEMO 品質不佳（能量 = {E_demo:.2e}）")
    else:
        print(f"✅ DEMO 能量 = {E_demo:.4e}")
    
    return float(E_demo)


def analyze_high_peaks(i, zc_all, r_detrend, t, fs, energy_threshold, dir_eff, spans):
    """用能量密度判定正半波"""
    s, e = zc_all[i], zc_all[i + 1]
    seg = r_detrend[s:e]
    seg_t = t[s:e]

    if len(seg) == 0 or len(seg_t) == 0:
        return spans

    duration = seg_t[-1] - seg_t[0]
    
    if duration < MIN_ACTION_DURATION:
        return spans
    
    if duration > MAX_ACTION_DURATION:
        print(f"⚠️ 異常：動作持續 {duration:.1f}秒（超過 {MAX_ACTION_DURATION}秒限制）")
        return spans

    seg_energy = energy_density_interval_dir(seg, seg_t, fs, seg_t[0], seg_t[-1], dir_eff)

    if seg_energy >= energy_threshold:
        peak_val = np.max(seg)
        peak_idx = np.argmax(seg)
        peak_time = t[s + peak_idx]
        
        st, ed = t[s], t[e]
        spans.append({
            "start_time": round(st, 3),
            "end_time": round(ed, 3),
            "peak_time": round(peak_time, 3),
            "peak_value": round(peak_val, 6),
            "energy": round(seg_energy, 10),
            "duration": round(ed - st, 3),
            "dir": "P"
        })
    return spans


def analyze_low_troughs(i, zc_all, r_detrend, t, fs, energy_threshold, dir_eff, spans):
    """用能量密度判定負半波"""
    s, e = zc_all[i], zc_all[i + 1]
    seg = r_detrend[s:e]
    seg_t = t[s:e]

    if len(seg) == 0 or len(seg_t) == 0:
        return spans

    duration = seg_t[-1] - seg_t[0]
    
    if duration < MIN_ACTION_DURATION:
        return spans
    
    if duration > MAX_ACTION_DURATION:
        print(f"⚠️ 異常：動作持續 {duration:.1f}秒（超過 {MAX_ACTION_DURATION}秒限制）")
        return spans

    seg_energy = energy_density_interval_dir(seg, seg_t, fs, seg_t[0], seg_t[-1], dir_eff)

    if seg_energy >= energy_threshold:
        trough_val = np.min(seg)
        trough_idx = np.argmin(seg)
        trough_time = t[s + trough_idx]
        
        st, ed = t[s], t[e]
        spans.append({
            "start_time": round(st, 3),
            "end_time": round(ed, 3),
            "trough_time": round(trough_time, 3),
            "trough_value": round(trough_val, 6),
            "energy": round(seg_energy, 10),
            "duration": round(ed - st, 3),
            "dir": "N"
        })
    return spans


def merge_segments_by_time(spans, max_bridge_sec):
    """合併相鄰且間隔很短的同方向動作"""
    if len(spans) < 2:
        return spans
    
    changed = True
    while changed:
        changed = False
        merged = []
        i = 0

        while i < len(spans):
            if i + 1 < len(spans):
                s0, s1 = spans[i], spans[i+1]
                
                # 同方向 + 間隔很短 → 合併
                gap = s1["start_time"] - s0["end_time"]
                if s0["dir"] == s1["dir"] and gap <= max_bridge_sec:
                    new_seg = {
                        "start_time": s0["start_time"],
                        "end_time": s1["end_time"],
                        "duration": s1["end_time"] - s0["start_time"],
                        "dir": s0["dir"],
                        "energy": s0.get("energy", 0) + s1.get("energy", 0)
                    }

                    if s0["dir"] == "P":
                        new_seg["peak_value"] = max(s0["peak_value"], s1["peak_value"])
                        new_seg["peak_time"] = s0["peak_time"] if s0["peak_value"] >= s1["peak_value"] else s1["peak_time"]
                    else:
                        new_seg["trough_value"] = min(s0["trough_value"], s1["trough_value"])
                        new_seg["trough_time"] = s0["trough_time"] if s0["trough_value"] <= s1["trough_value"] else s1["trough_time"]

                    merged.append(new_seg)
                    i += 2
                    changed = True
                    continue

            merged.append(spans[i])
            i += 1
        
        spans = merged

    return spans


def analyze_csv(file_path: str, cutoff: float = 0.8, order: int = 4,
                threshold: float = 0.0008, dir_default: str = "N") -> dict:
    """分析 CSV 檔案，自動判斷方向並計算動作次數"""
    # 打開註解(電腦版)3
    global _plot_cache
    
    try:
        df = pd.read_csv(file_path)
        cols = {c.lower(): c for c in df.columns}

        if "outer_mouth_z_avg" in cols:
            point_name = "outer_mouth_z_avg"
            output_name = "POUT_LIPS"
        elif "total_lip_area" in cols:
            point_name = "total_lip_area"
            output_name = "SIP_LIPS"
        else:
            return {"status": "ERROR", "action_count": 0, "error": "找不到欄位"}

        if "time_seconds" not in cols or point_name not in cols:
            return {"status": "ERROR", "action_count": 0, "error": "缺少欄位"}

        df_all = df.copy()
        dir_eff = infer_dir_from_demo(df_all, cols, point_name, dir_default)

        if "state" in cols:
            df_main = df_all[df_all[cols["state"]] == "MAINTAINING"].copy()
        else:
            df_main = df_all.copy()

        t = pd.to_numeric(df_main[cols["time_seconds"]], errors="coerce").to_numpy()
        r = pd.to_numeric(df_main[cols[point_name]], errors="coerce").to_numpy()
        m = np.isfinite(t) & np.isfinite(r)
        t, r = t[m], r[m]

        fs = calculate_fs_from_csv(file_path)

        t_all_full = pd.to_numeric(df_all[cols["time_seconds"]], errors="coerce").to_numpy()
        r_all_full = pd.to_numeric(df_all[cols[point_name]], errors="coerce").to_numpy()
        m_full = np.isfinite(t_all_full) & np.isfinite(r_all_full)
        t_all_full, r_all_full = t_all_full[m_full], r_all_full[m_full]

        if len(t) < 2:
            # 打開註解(電腦版)4
            _plot_cache = None
            return {"status": "OK", "action_count": 0, "segments": []}

        cutoff_final, _ = auto_cutoff_from_signal(t, r, fs)
        
        r_all_filt = lowpass_filter(r_all_full, fs=fs, cutoff=cutoff_final, order=order)
        
        baseline_window = max(3, min(int(7.0 * fs), len(r_all_filt) // 2))
        baseline_all = moving_average(r_all_filt, baseline_window)
        
        r_all_detrend = r_all_filt - baseline_all

        mask_maintaining = np.zeros(len(t_all_full), dtype=bool)
        if "state" in cols:
            s_all = df_all[cols["state"]].astype(str)
            mask_maintaining_all = s_all.str.contains("MAINTAINING", case=False, na=False).to_numpy()
            mask_maintaining = mask_maintaining_all[m_full]
        
        t_main = t_all_full[mask_maintaining]
        r_detrend = r_all_detrend[mask_maintaining]
        r_filt = r_all_filt[mask_maintaining]
        baseline = baseline_all[mask_maintaining]

        # 零交叉偵測
        zc_all = zero_crossings(r_detrend, t_main, deadband=0.0, min_interval=int(0.5 * fs))

        # === 補頭尾邊界（確保頭尾段不被漏掉）===
        if len(zc_all) == 0:
            zc_all = [0, len(r_detrend) - 1]
        else:
            if zc_all[0] > 0:
                zc_all = [0] + zc_all
            if zc_all[-1] < len(r_detrend) - 1:
                zc_all = zc_all + [len(r_detrend) - 1]

        # DEMO 能量
        mask_demo = None
        if "state" in cols:
            s_all = df_all[cols["state"]].astype(str)
            mask_demo_all = s_all.str.contains("DEMO", case=False, na=False).to_numpy()
            mask_demo = mask_demo_all[m_full]

        demo_E = compute_demo_energy_from_baseline(t_all_full, r_all_full, mask_demo, r_all_detrend, fs, dir_eff)

        # === 備援門檻（DEMO 能量不足時）===
        if demo_E < MIN_DEMO_ENERGY:
            # 用 MAINTAINING 段 detrend 的標準差估門檻
            std_main = np.std(r_detrend) if len(r_detrend) > 0 else 1e-6
            energy_threshold = 0.15 * std_main  # 約 15% 的 std
            print(f"⚠️ DEMO 能量不足，使用備援門檻 = {energy_threshold:.4e}")
        else:
            energy_threshold = R_DEMO * demo_E
        
        print(f"🔥 門檻 = {energy_threshold:.4e}")

        # 分析各段
        spans = []
        for i in range(len(zc_all) - 1):
            s, e = zc_all[i], zc_all[i + 1]
            seg = r_detrend[s:e]
            if len(seg) == 0:
                continue

            seg_mean = np.mean(seg)

            if dir_eff == "P" and seg_mean > 0:
                spans = analyze_high_peaks(i, zc_all, r_detrend, t_main, fs, energy_threshold, dir_eff, spans)
            elif dir_eff == "N" and seg_mean < 0:
                spans = analyze_low_troughs(i, zc_all, r_detrend, t_main, fs, energy_threshold, dir_eff, spans)

        # 合併
        spans.sort(key=lambda x: x["start_time"])
        spans = merge_segments_by_time(spans, BRIDGE_MAX_SEC)

        total_action_time = round(sum(action["duration"] for action in spans), 3)
        breakpoints = [action["end_time"] for action in spans]

        segments = []
        for i, action in enumerate(spans):
            segments.append({
                "index": i,
                "start_time": action["start_time"],
                "end_time": action["end_time"],
                "duration": action["duration"]
            })

        # 打開註解(電腦版)5
        cal_start = cal_end = action_start = None
        if "state" in cols:
            s_all = df_all[cols["state"]].astype(str)
            mask_cal_all = s_all.str.contains("CAL", case=False, na=False).to_numpy()
            mask_act_all = s_all.str.contains("MAINTAINING", case=False, na=False).to_numpy()
        
            mask_cal = mask_cal_all[m_full]
            mask_act = mask_act_all[m_full]
        
            idx_cal = np.flatnonzero(mask_cal)
            if idx_cal.size > 0:
                cal_start = float(t_all_full[idx_cal[0]])
                cal_end = float(t_all_full[idx_cal[-1]])
        
            idx_act = np.flatnonzero(mask_act)
            if idx_act.size > 0:
                action_start = float(t_all_full[idx_act[0]])

        # 打開註解(電腦版)6
        _plot_cache = {
            "r_all_detrend": r_all_detrend,
            "csv_path": file_path,
            "t_all": t_all_full,
            "r_all": r_all_full,
            "t": t_main,
            "r_filt": r_filt,
            "baseline": baseline,
            "r_detrend": r_detrend,
            "zc_all": zc_all,
            "spans": spans,
            "dir_eff": dir_eff,
            "fs": fs,
            "cutoff_final": cutoff_final,
            "cal_start": cal_start,
            "cal_end": cal_end,
            "action_start": action_start,
            "mask_demo": mask_demo,
            "energy_threshold": energy_threshold,
            "output_name": output_name,
        }

        return {
            "status": "OK",
            "action_count": len(spans),
            "total_action_time": total_action_time,
            "breakpoints": breakpoints,
            "segments": segments,
            "debug": {
                "fs_hz": fs,
                "cutoff": cutoff_final,
                "order": order,
                "zc_all": len(zc_all),
                "zc_up": 0,      # ← 補這個
                "zc_down": 0,    # ← 補這個
                "deadband": 0.0,
                "min_interval": int(0.5 * fs),
                "demo_E": demo_E,
                "energy_threshold": energy_threshold,
            }
        }

    except Exception as e:
        import traceback
        # 打開註解(電腦版)7
        _plot_cache = None
        return {
            "status": "ERROR",
            "action_count": 0,
            "error": str(e),
            "traceback": traceback.format_exc()
        }


# 打開註解(電腦版) - 畫圖函數
def plot_lips_analysis(csv_path, t_all, r_all, r_all_detrend, t, r_filt, baseline, r_detrend,
                       zc_all, spans, dir_eff, fs, cutoff_final,
                       cal_start, cal_end, action_start, mask_demo,
                       energy_threshold, output_name):
    """
    LIPS 版畫圖
    - 單張圖
    - DEMO 黃色背景
    - 時間軸秒數
    - 能量門檻虛線
    - 動作區間色塊
    """
    fig, ax = plt.subplots(figsize=(14, 5))

    # ========== 背景 (DEMO 黃色區塊) ==========
    if mask_demo is not None and mask_demo.any():
        idx_demo = np.flatnonzero(mask_demo)
        cuts = np.where(np.diff(idx_demo) > 1)[0] + 1
        for g in np.split(idx_demo, cuts):
            if len(g) > 0:
                st, ed = t_all[g[0]], t_all[g[-1]]
                ax.axvspan(st, ed, facecolor="#FFF3CD", alpha=0.45, zorder=0)

    # ========== 主曲線（全段）==========
    ax.plot(t_all, r_all, label="raw", color="#1976D2", linewidth=1.0, alpha=0.7)

    # 重新計算全段濾波 + baseline
    r_all_filt = lowpass_filter(r_all, fs=fs, cutoff=cutoff_final, order=ORDER)
    baseline_window = max(3, min(int(7.0 * fs), len(r_all_filt) // 2))
    baseline_all = moving_average(r_all_filt, baseline_window)

    ax.plot(t_all, r_all_filt, label=f"filt({cutoff_final:.2f}Hz)", color="#FB8C00", linewidth=1.1)
    ax.plot(t_all, baseline_all, label="baseline", color="#43A047", linewidth=1.3)
    ax.plot(t_all, r_all_detrend, label="detrended (全段)", color="red", linewidth=1.2)

    # ========== 能量門檻虛線 ==========
    ax.axhline(+energy_threshold, color="#9C27B0", linestyle="--", linewidth=1.2, alpha=0.8,
               label=f"+E_thr={energy_threshold:.2e}")
    ax.axhline(-energy_threshold, color="#9C27B0", linestyle="--", linewidth=1.2, alpha=0.8,
               label=f"-E_thr={energy_threshold:.2e}")

    # 中心線
    ax.axhline(0, color="black", linestyle="--", linewidth=1.0, alpha=0.6)

    # y 偏移給文字
    if len(r_detrend) > 0:
        yr = max(np.max(r_detrend) - np.min(r_detrend), 1e-12)
        y_offset = 0.04 * yr
    else:
        y_offset = 1e-6

    # ========== 強調通過門檻的動作 ==========
    for d in spans:
        if "peak_time" in d:  # 正半波 (P)
            ax.axvspan(d["start_time"], d["end_time"],
                       facecolor="crimson", alpha=0.45)
            ax.text(d["peak_time"], d["peak_value"] + y_offset,
                    f"E={d['energy']:.2e}",
                    color="crimson", fontsize=8, ha="center")
        elif "trough_time" in d:  # 負半波 (N)
            ax.axvspan(d["start_time"], d["end_time"],
                       facecolor="royalblue", alpha=0.45)
            ax.text(d["trough_time"], d["trough_value"] - y_offset,
                    f"E={d['energy']:.2e}",
                    color="royalblue", fontsize=8, ha="center")

    # ========== 零交叉線 ==========
    for idx in zc_all:
        if 0 <= idx < len(t):
            ax.axvline(t[idx], color="#42A5F5", linestyle="-", linewidth=0.7, alpha=0.7)

    # ========== 標題 ==========
    ax.set_title(
        f"{output_name} | dir={dir_eff} | cutoff={cutoff_final:.2f}Hz | fs={fs:.1f}Hz | count={len(spans)}"
    )
    ax.set_xlabel("time (sec)")
    ax.set_ylabel("signal value")
    ax.legend(loc="upper left", fontsize=8)

    # ========== X 軸格式（秒數）==========
    ax.set_xlim(float(np.floor(t_all.min())), float(np.ceil(t_all.max())))
    ax.xaxis.set_major_locator(MultipleLocator(1.0))

    def piece_fmt(x, pos=None):
        if (cal_start is not None) and (cal_end is not None) and (x <= cal_end):
            return str(int(round(x - cal_start)))
        if (action_start is not None) and (x >= action_start):
            return str(int(round(x - action_start)))
        return str(int(round(x)))

    ax.xaxis.set_major_formatter(FuncFormatter(piece_fmt))
    ax.grid(True, axis='x', linestyle='--', alpha=0.25)

    fig.tight_layout()

    # ========== 存檔 ==========
    out_dir = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\嘟嘴抿嘴跑圖"
    os.makedirs(out_dir, exist_ok=True)
    base = os.path.splitext(os.path.basename(csv_path))[0]
    out_path = os.path.join(out_dir, f"{base}_plot.png")
    fig.savefig(out_path, dpi=300)

    print("✔ 圖片輸出：", out_path)

    plt.show()
    plt.close(fig)


def debug_plot_last():
    """
    用 analyze_csv() 算完後存的 _plot_cache 來畫圖
    """
    if not HAS_PLOT:
        return
    
    global _plot_cache
    if _plot_cache is None:
        print("⚠️ 沒有可畫圖的快取資料（先呼叫 analyze_csv 才能畫）")
        return

    d = _plot_cache
    plot_lips_analysis(
        csv_path=d["csv_path"],
        t_all=d["t_all"],
        r_all=d["r_all"],
        r_all_detrend=d["r_all_detrend"],
        t=d["t"],
        r_filt=d["r_filt"],
        baseline=d["baseline"],
        r_detrend=d["r_detrend"],
        zc_all=d["zc_all"],
        spans=d["spans"],
        dir_eff=d["dir_eff"],
        fs=d["fs"],
        cutoff_final=d["cutoff_final"],
        cal_start=d["cal_start"],
        cal_end=d["cal_end"],
        action_start=d["action_start"],
        mask_demo=d["mask_demo"],
        energy_threshold=d["energy_threshold"],
        output_name=d["output_name"],
    )


# ===== 測試用 =====
if __name__ == "__main__":
    # 直接列出所有要分析的檔案
    csv_files = [
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251117_184442.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251104_035220_7.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_175509.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_175330.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_171143.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_170951.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_154810.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_153616.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_140621.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\POUT_LIPS\FaceTraining_POUT_LIPS_20251103_140521.csv",
r"C:\Users\plus1\Downloads\1128_收案day1\testuser01_FaceTraining_POUT_LIPS_20251128_155732.csv",
r"C:\Users\plus1\Downloads\1128_收案day1\testuser01_FaceTraining_POUT_LIPS_20251128_155926.csv"
    ]
    
    print("=" * 80)
    all_results = {}
    
    for i, csv_path in enumerate(csv_files, 1):
        filename = csv_path.split("\\")[-1]
        
        print(f"\n[{i}/{len(csv_files)}] 📁 {filename}")
        print("-" * 80)
        
        result = analyze_csv(csv_path)
        all_results[filename] = result
        
        print(f"狀態: {result.get('status')}")
        print(f"動作次數: {result.get('action_count')}")
        print(f"總動作時間: {result.get('total_action_time')} 秒")
        
        if result.get('status') == 'ERROR':
            print(f"❌ 錯誤: {result.get('error')}")
        
        # 畫圖
        debug_plot_last()
    
    # 總結
    print("\n" + "=" * 80)
    print("📊 總結")
    print("=" * 80)
    
    total_actions = 0
    for filename, result in all_results.items():
        count = result.get('action_count', 0)
        total_actions += count
        status = "✅" if result.get('status') == 'OK' else "❌"
        print(f"{filename:<70} {status} {count}")
    
    print(f"\n總動作數: {total_actions}")
    print("=" * 80)