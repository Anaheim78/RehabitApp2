import io
import csv
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt
from scipy.signal import correlate, find_peaks

# PC版
# import matplotlib.pyplot as plt
# from matplotlib.ticker import MultipleLocator, FuncFormatter
# import os
# from matplotlib import rcParams

# # 設定中文字型
# rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'Arial Unicode MS', 'sans-serif']
# rcParams['axes.unicode_minus'] = False  # 解決負號顯示問題

# === DEMO 能量參數 ===
DEMO_SIDE_SEC = 2.0
R_DEMO = 0.3  # 🔥 改用 R_DEMO（門檻 = DEMO 能量的倍數）
MIN_ACTION_DURATION = 0.4  # 🔥 新增：最小動作時間
MAX_ACTION_DURATION = 3.0  # 🔥 新增：最大動作時間
MIN_DEMO_ENERGY = 1e-5  # 🔥 新增：DEMO 最小能量門檻

# PC=== 畫圖快取 ===
# _plot_cache = None


def auto_cutoff_from_signal(t, r, fs,
                            min_period_sec=0.5,
                            max_period_sec=5.0,
                            gain_over_f0=1.5,
                            min_cut=0.25,
                            max_cut_cap=2.0):
    """
    根據信號週期自動估 cutoff 頻率，並回傳區段清單。
    """
    x = np.asarray(r, dtype=float)
    if x.size < int(2 * fs):
        return 0.8, []

    # 全域濾波 & 基線
    b, a = butter(4, min(1.5, 0.49 * fs) / (fs / 2), btype='low')
    x_f = filtfilt(b, a, x)
    base = moving_average(x_f, int(3 * fs))
    xd = x_f - base
    xn = (xd - np.mean(xd)) / (np.std(xd) + 1e-12)

    # 自相關
    ac = correlate(xn, xn, mode='full')[len(xn)-1:]
    min_lag, max_lag = int(min_period_sec * fs), int(max_period_sec * fs)
    peaks, _ = find_peaks(ac[min_lag:max_lag], prominence=0.05)
    if len(peaks) == 0:
        return 0.8, []
    lag_main = peaks[np.argmax(ac[min_lag + peaks])]
    f0_main = fs / (lag_main + min_lag)
    cutoff_main = np.clip(gain_over_f0 * f0_main, min_cut, min(max_cut_cap, 0.49*fs))

    # 分段估計
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
    cutoff_final = 2.0  # 🔥 寫死 1.0 Hz（跟嘟嘴一樣）
    print(f"✅ 自動cutoff完成 → 最終={cutoff_final:.2f}Hz, 區段數={len(segments)}")
    return cutoff_final, segments

# ===== 自動計算取樣率（僅用 MAINTAINING）=====
def calculate_fs_from_csv(file_path: str) -> float:
    """
    統計CSV中排除頭尾後，穩定區的最低幀數作為FS
    """
    df = pd.read_csv(file_path)
    if "state" in df.columns:
        df = df[df["state"] == "MAINTAINING"]
    if len(df) < 2 or "time_seconds" not in df.columns:
        return 10.0
    t = pd.to_numeric(df["time_seconds"], errors="coerce").to_numpy()
    t = t[np.isfinite(t)]
    if len(t) < 2:
        return 10.0

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
        return 10.0

    min_fps = min(stable_counts)
    print(f"📊 FS(auto) = {min_fps} Hz")
    return float(min_fps)

# ===== DEMO 判斷主方向 =====
def infer_dir_from_demo(df, cols, point_name, dir_default="N"):
    """
    從 DEMO 段自動推斷方向（面積法）
    P: 正半波 (往上)
    N: 負半波 (往下)
    """
    if "state" not in cols or "time_seconds" not in cols:
        return dir_default

    s_col = cols["state"]
    t_col = cols["time_seconds"]
    mask_demo = df[s_col].astype(str).str.contains("DEMO", case=False, na=False)
    if not mask_demo.any():
        print(f"⚠️ 找不到 DEMO 段，預設使用 {dir_default}")
        return dir_default

    # 取 DEMO 段的時間與訊號
    t_all = pd.to_numeric(df[t_col], errors="coerce").to_numpy()
    r_all = pd.to_numeric(df[cols[point_name]], errors="coerce").to_numpy()
    m_valid = np.isfinite(t_all) & np.isfinite(r_all)
    t_all, r_all = t_all[m_valid], r_all[m_valid]
    mask_demo = mask_demo.to_numpy()[m_valid]

    if np.sum(mask_demo) < 5:
        print(f"⚠️ DEMO 資料不足，預設使用 {dir_default}")
        return dir_default

    t_demo = t_all[mask_demo]
    r_demo = r_all[mask_demo]
    t0, t1 = t_demo[0], t_demo[-1]
    side_sec = 2.0  # DEMO 兩側取樣區間

    mask_left = (t_all >= t0 - side_sec) & (t_all < t0)
    mask_right = (t_all > t1) & (t_all <= t1 + side_sec)

    if np.sum(mask_left) < 3 or np.sum(mask_right) < 3:
        print(f"⚠️ DEMO 兩側資料不足，預設使用 {dir_default}")
        return dir_default

    r_left_avg = np.mean(r_all[mask_left])
    r_right_avg = np.mean(r_all[mask_right])

    # 建立基準線（連接左右平均）
    baseline = np.interp(t_demo, [t_demo[0], t_demo[-1]], [r_left_avg, r_right_avg])
    diff = r_demo - baseline

    # 計算上下方面積
    area_pos = np.trapz(diff[diff > 0], t_demo[diff > 0]) if np.any(diff > 0) else 0
    area_neg = np.trapz(-diff[diff < 0], t_demo[diff < 0]) if np.any(diff < 0) else 0

    dir_auto = "P" if area_pos > area_neg else "N"
    print(f"📈 DEMO 面積法方向: +面積={area_pos:.4f}, -面積={area_neg:.4f} → 使用 {dir_auto}")
    return dir_auto

# ===== 低通濾波器 =====
def lowpass_filter(x, fs=25.0, cutoff=0.5, order=4):
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    return filtfilt(b, a, x)

# ===== 移動平均（基線估計）=====
def moving_average(x, win_samples=20):
    # 🔥 加入安全檢查
    win_samples = max(3, min(win_samples, len(x) // 2))
    kernel = np.ones(win_samples) / win_samples
    pad_width = win_samples // 2
    x_padded = np.pad(x, pad_width, mode='edge')
    baseline_full = np.convolve(x_padded, kernel, mode='same')
    return baseline_full[pad_width:-pad_width]

# ===== 零交叉檢測 =====
def zero_crossings(x, t, deadband=0.0, min_interval=10):
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

# ===== 半波分析：正半波（峰值）=====
def analyze_high_peaks(i, zc_all, r_detrend, t, fs, energy_threshold, dir_eff, spans):
    s, e = zc_all[i], zc_all[i + 1]
    seg = r_detrend[s:e]
    seg_t = t[s:e]

    # 🔥 時間限制檢查
    duration = seg_t[-1] - seg_t[0]

    if duration < MIN_ACTION_DURATION:
        return spans

    if duration > MAX_ACTION_DURATION:
        print(f"⚠️ 異常：動作持續 {duration:.1f}秒")
        return spans

    # 計算能量密度
    seg_energy = energy_density_interval_dir(
        seg, seg_t, fs, seg_t[0], seg_t[-1], dir_eff
    )

    # 用能量判定
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
            "energy_thr": round(energy_threshold, 10),
            "duration": round(ed - st, 3)
        })
    return spans

# ===== 半波分析：負半波（谷值）=====
def analyze_low_troughs(i, zc_all, r_detrend, t, fs, energy_threshold, dir_eff, spans):
    """用能量密度判定負半波"""
    s, e = zc_all[i], zc_all[i + 1]
    seg = r_detrend[s:e]
    seg_t = t[s:e]

    # 計算能量密度
    seg_energy = energy_density_interval_dir(
        seg, seg_t, fs, seg_t[0], seg_t[-1], dir_eff
    )

    # 用能量判定
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
            "energy_thr": round(energy_threshold, 10),
            "duration": round(ed - st, 3)
        })
    return spans

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

    total = np.sum(vals)  # 🔥 不除 fs（跟嘟嘴一致）
    dur = vals.size / fs
    return float(total / max(dur, 1e-9))

def compute_demo_energy_from_baseline(t_all, r_all, mask_demo, r_all_detrend, fs, dir_eff):
    """用全段統一的 detrend 計算 DEMO 能量"""
    if mask_demo is None or mask_demo.sum() < 3:
        return 0.0

    idx_demo = np.flatnonzero(mask_demo)
    if idx_demo.size < 2:
        return 0.0

    t_demo_start = float(t_all[idx_demo[0]])
    t_demo_end = float(t_all[idx_demo[-1]])

    # 🔥 取 DEMO 前 2 秒當靜止基準
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

    # 🔥 DEMO 品質檢查
    if E_demo < MIN_DEMO_ENERGY:
        print(f"❌ DEMO 品質極差（能量 = {E_demo:.2e} < {MIN_DEMO_ENERGY:.2e}）")
    elif E_demo < MIN_DEMO_ENERGY * 10:
        print(f"⚠️ DEMO 品質不佳（能量 = {E_demo:.2e}）")
    else:
        print(f"✅ DEMO 能量 = {E_demo:.4e}")

    return float(E_demo)

# ===== 主分析流程 =====
def analyze_csv(file_path: str, cutoff: float = 0.8, order: int = 4,
                threshold: float = 0.0008, dir_default: str = "N") -> dict:
    """
    分析 CSV 檔案，自動判斷方向並計算動作次數
    """
    # PC
    # global _plot_cache

    try:
        # 讀取 CSV
        df = pd.read_csv(file_path)
        cols = {c.lower(): c for c in df.columns}

        # 判斷任務類型
        if "outer_mouth_z_avg" in cols:
            point_name = "outer_mouth_z_avg"
            output_name = "POUT_LIPS"
        elif "total_lip_area" in cols:
            point_name = "total_lip_area"
            output_name = "SIP_LIPS"
        else:
            return {
                "status": "ERROR",
                "action_count": 0,
                "segments": [],
                "error": "找不到 outer_mouth_z_avg 或 total_lip_area 欄位"
            }

        # 檢查必要欄位
        if "time_seconds" not in cols or point_name not in cols:
            return {
                "status": "ERROR",
                "action_count": 0,
                "segments": [],
                "error": "缺少必要欄位"
            }

        # 全段資料（用於判斷方向）
        df_all = df.copy()

        # 判斷方向（從 DEMO）
        dir_eff = infer_dir_from_demo(df_all, cols, point_name, dir_default)

        # 只保留 MAINTAINING 階段
        if "state" in cols:
            df_main = df_all[df_all[cols["state"]] == "MAINTAINING"].copy()
        else:
            df_main = df_all.copy()

        # 提取時間與訊號
        t = pd.to_numeric(df_main[cols["time_seconds"]], errors="coerce").to_numpy()
        r = pd.to_numeric(df_main[cols[point_name]], errors="coerce").to_numpy()
        m = np.isfinite(t) & np.isfinite(r)
        t, r = t[m], r[m]

        # 自動計算取樣率
        fs = calculate_fs_from_csv(file_path)

        # === 全段資料（用於畫圖）===
        t_all_full = pd.to_numeric(df_all[cols["time_seconds"]], errors="coerce").to_numpy()
        r_all_full = pd.to_numeric(df_all[cols[point_name]], errors="coerce").to_numpy()
        m_full = np.isfinite(t_all_full) & np.isfinite(r_all_full)
        t_all_full, r_all_full = t_all_full[m_full], r_all_full[m_full]

        if len(t) < 2:
            # PC
            # _plot_cache = None
            return {
                "status": "OK",
                "action_count": 0,
                "total_action_time": 0.0,
                "breakpoints": [],
                "segments": [],
                "debug": {
                    "fs_hz": fs,
                    "cutoff": cutoff,
                    "order": order,
                    "zc_all": 0,
                    "zc_up": 0,
                    "zc_down": 0,
                    "deadband": 0.0,
                    "min_interval": int(0.2 * fs)
                }
            }

        # 濾波
        # 🔥 改用全段統一處理
        cutoff_final, _ = auto_cutoff_from_signal(t, r, fs)

        # 濾波全段
        r_all_filt = lowpass_filter(r_all_full, fs=fs, cutoff=cutoff_final, order=order)

        # 全段 baseline
        baseline_window = max(3, min(int(4.0 * fs), len(r_all_filt) // 2))
        baseline_all = moving_average(r_all_filt, baseline_window)

        # 全段 detrend
        r_all_detrend = r_all_filt - baseline_all

        # 只取 MAINTAINING 的部分
        mask_maintaining = np.zeros(len(t_all_full), dtype=bool)
        if "state" in cols:
            s_all = df_all[cols["state"]].astype(str)
            mask_maintaining_all = s_all.str.contains("MAINTAINING", case=False, na=False).to_numpy()
            mask_maintaining = mask_maintaining_all[m_full]

        t_main = t_all_full[mask_maintaining]
        r_detrend = r_all_detrend[mask_maintaining]
        r_filt = r_all_filt[mask_maintaining]
        baseline = baseline_all[mask_maintaining]

        # 零交叉
        zc_all = zero_crossings(r_detrend, t_main, deadband=0.0, min_interval=int(0.2 * fs))

        mask_demo = None
        if "state" in cols:
            s_all = df_all[cols["state"]].astype(str)
            mask_demo_all = s_all.str.contains("DEMO", case=False, na=False).to_numpy()
            mask_demo = mask_demo_all[m_full]

        # 🔥 新版：計算 DEMO 能量
        demo_E = compute_demo_energy_from_baseline(
            t_all_full, r_all_full, mask_demo, r_all_detrend, fs, dir_eff
        )

        # # 🔥 DEMO 品質檢查
        # if demo_E < MIN_DEMO_ENERGY:
        #     return {
        #         "status": "ERROR",
        #         "action_count": 0,
        #         "error": f"DEMO 品質不足（能量 = {demo_E:.2e}）",
        #     }

        # 🔥 新版門檻
        energy_threshold = R_DEMO * demo_E
        print(f"🔥 門檻 = {energy_threshold:.4e} ({R_DEMO} × DEMO)")

        # 分析半波
        spans = []
        for i in range(len(zc_all) - 1):
            s, e = zc_all[i], zc_all[i + 1]
            seg = r_detrend[s:e]
            if len(seg) == 0:
                continue

            seg_mean = np.mean(seg)

            # 根據方向選擇正或負半波
            if dir_eff == "P" and seg_mean > 0:
                spans = analyze_high_peaks(i, zc_all, r_detrend, t, fs, energy_threshold, dir_eff, spans)
            elif dir_eff == "N" and seg_mean < 0:
                spans = analyze_low_troughs(i, zc_all, r_detrend, t, fs, energy_threshold, dir_eff, spans)

        # 計算總動作時間
        total_action_time = round(sum(action["duration"] for action in spans), 3)

        # 建立斷點列表
        breakpoints = [action["end_time"] for action in spans]

        # 轉換成 segments 格式
        segments = []
        for i, action in enumerate(spans):
            segments.append({
                "index": i,
                "start_time": action["start_time"],
                "end_time": action["end_time"],
                "duration": action["duration"]
            })

        # === 提取 state 相關資訊（用於畫圖）===
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

        # === 存畫圖快取 ===
        # _plot_cache = {
        #     "csv_path": file_path,
        #     "t_all": t_all_full,
        #     "r_all": r_all_full,
        #     "t": t,
        #     "r_filt": r_filt,
        #     "baseline": baseline,
        #     "r_detrend": r_detrend,
        #     "zc_all": zc_all,
        #     "spans": spans,
        #     "dir_eff": dir_eff,
        #     "fs": fs,
        #     "cutoff_final": cutoff_final,
        #     "cal_start": cal_start,
        #     "cal_end": cal_end,
        #     "action_start": action_start,
        #     "mask_demo": mask_demo,
        #     "energy_threshold": energy_threshold,
        #     "output_name": output_name,
        # }

        # 返回結果
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
                "zc_up": 0,
                "zc_down": 0,
                "deadband": 0.0,
                "min_interval": int(0.2 * fs),
                "demo_E": demo_E,
                "demo_E_noise": 0.0,  # 新版沒算，填 0
                "demo_E_thr": demo_E * (R_DEMO / 6.0),  # 相容舊格式
                "energy_threshold": energy_threshold,
            }
        }

    except Exception as e:
        import traceback
        # PC
        # _plot_cache = None
        error_msg = str(e)
        return {
            "status": "ERROR",
            "action_count": 0,
            "total_action_time": 0.0,
            "breakpoints": [],
            "segments": [],
            "error": error_msg,
            "traceback": traceback.format_exc()
        }


# ===== 畫圖函數 =====
# def plot_lips_analysis(csv_path, t_all, r_all, t, r_filt, baseline, r_detrend,
#                        zc_all, spans, dir_eff, fs, cutoff_final,
#                        cal_start, cal_end, action_start, mask_demo,
#                        energy_threshold, output_name):

#     fig, ax = plt.subplots(figsize=(14, 5))

#     # ========== 背景 (DEMO) ==========
#     if mask_demo is not None and mask_demo.any():
#         idx_demo = np.flatnonzero(mask_demo)
#         cuts = np.where(np.diff(idx_demo) > 1)[0] + 1
#         for g in np.split(idx_demo, cuts):
#             st, ed = t_all[g[0]], t_all[g[-1]]
#             ax.axvspan(st, ed, facecolor="#FFF3CD", alpha=0.45, zorder=0)

#     # ========== 主曲線 ==========
#     ax.plot(t_all, r_all, label="raw", color="#1976D2", linewidth=1.0, alpha=0.7)
#     ax.plot(t, r_filt, label=f"filt({cutoff_final:.2f}Hz)", color="#FB8C00", linewidth=1.1)
#     ax.plot(t, baseline, label="baseline", color="#43A047", linewidth=1.3)
#     ax.plot(t, r_detrend, label="detrended", color="red", linewidth=1.2)

#     # ========== 能量門檻虛線 ==========
#     ax.axhline(+energy_threshold, color="#9C27B0", linestyle="--", linewidth=1.2, alpha=0.8,
#                label=f"+E_thr={energy_threshold:.2e}")
#     ax.axhline(-energy_threshold, color="#9C27B0", linestyle="--", linewidth=1.2, alpha=0.8,
#                label=f"-E_thr={energy_threshold:.2e}")

#     # 中心線
#     ax.axhline(0, color="black", linestyle="--", linewidth=1.0, alpha=0.6)

#     # y 偏移給文字
#     if len(r_detrend) > 0:
#         yr = max(np.max(r_detrend) - np.min(r_detrend), 1e-12)
#         y_offset = 0.04 * yr
#     else:
#         y_offset = 1e-6

#     # ========== 強調通過門檻的動作 ==========
#     for d in spans:
#         if "peak_time" in d:  # 正半波
#             ax.axvspan(d["start_time"], d["end_time"],
#                        facecolor="crimson", alpha=0.45)
#             ax.text(d["peak_time"], d["peak_value"] + y_offset,
#                     f"E={d['energy']:.2e}",
#                     color="crimson", fontsize=8, ha="center")
#         elif "trough_time" in d:  # 負半波
#             ax.axvspan(d["start_time"], d["end_time"],
#                        facecolor="royalblue", alpha=0.45)
#             ax.text(d["trough_time"], d["trough_value"] - y_offset,
#                     f"E={d['energy']:.2e}",
#                     color="royalblue", fontsize=8, ha="center")

#     # ========== 零交叉 ==========
#     for idx in zc_all:
#         if 0 <= idx < len(t):
#             ax.axvline(t[idx], color="#42A5F5", linestyle="-", linewidth=0.7, alpha=0.7)

#     # ========== 標題 ==========
#     ax.set_title(
#         f"{output_name} | dir={dir_eff} | cutoff={cutoff_final:.2f}Hz | fs={fs:.1f}Hz | α={alpha}"
#     )

#     ax.set_ylabel("signal value")
#     ax.legend(loc="upper left", fontsize=8)

#     # ========== X 軸格式 ==========
#     ax.set_xlim(float(np.floor(t_all.min())), float(np.ceil(t_all.max())))
#     ax.xaxis.set_major_locator(MultipleLocator(1.0))

#     def piece_fmt(x, pos=None):
#         if (cal_start is not None) and (cal_end is not None) and (x <= cal_end):
#             return str(int(round(x - cal_start)))
#         if (action_start is not None) and (x >= action_start):
#             return str(int(round(26 - (x - action_start))))
#         return str(int(round(x)))

#     ax.xaxis.set_major_formatter(FuncFormatter(piece_fmt))
#     ax.grid(True, axis='x', linestyle='--', alpha=0.25)

#     fig.tight_layout()

#     # ========== 存檔 ==========
#     out_dir = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\抿嘴跑圖"
#     os.makedirs(out_dir, exist_ok=True)
#     base = os.path.splitext(os.path.basename(csv_path))[0]
#     out_path = os.path.join(out_dir, f"{base}_修正版_plot.png")
#     fig.savefig(out_path, dpi=300)

#     print("✔ 圖片輸出：", out_path)

#     plt.show()
#     plt.close(fig)


# def debug_plot_last():
#     """
#     用 analyze_csv() 算完後存的 _plot_cache 來畫圖
#     """

#     #PC
#     # global _plot_cache
#     if _plot_cache is None:
#         print("⚠️ 沒有可畫圖的快取資料（先呼叫 analyze_csv 才能畫）")
#         return

#     d = _plot_cache
#     plot_lips_analysis(
#         csv_path=d["csv_path"],
#         t_all=d["t_all"],
#         r_all=d["r_all"],
#         t=d["t"],
#         r_filt=d["r_filt"],
#         baseline=d["baseline"],
#         r_detrend=d["r_detrend"],
#         zc_all=d["zc_all"],
#         spans=d["spans"],
#         dir_eff=d["dir_eff"],
#         fs=d["fs"],
#         cutoff_final=d["cutoff_final"],
#         cal_start=d["cal_start"],
#         cal_end=d["cal_end"],
#         action_start=d["action_start"],
#         mask_demo=d["mask_demo"],
#         energy_threshold=d["energy_threshold"],
#         output_name=d["output_name"],
#     )


# ===== 測試 =====
if __name__ == "__main__":
    # 測試範例
    # ===== SIP_LIPS 測試檔案 =====

    # CSV_PATH = r"C:\Users\plus1\Downloads\1118從S24取出資料\FaceTraining_SIP_LIPS_20251117_184358.csv" #6/6
    # CSV_PATH = r"C:\Users\plus1\Downloads\1118從S24取出資料\FaceTraining_SIP_LIPS_20251113_145556.csv" # 9/9
    # CSV_PATH = r"C:\Users\plus1\Downloads\1118從S24取出資料\FaceTraining_SIP_LIPS_20251104_035124.csv" # 9/9
    # CSV_PATH = r"C:\Users\plus1\Downloads\1118從S24取出資料\FaceTraining_SIP_LIPS_20251103_175603.csv" # 5/6
    # CSV_PATH = r"C:\Users\plus1\Downloads\1118從S24取出資料\FaceTraining_SIP_LIPS_20251103_140958.csv" # 10/10
    # CSV_PATH = r"C:\Users\plus1\Downloads\1118從S24取出資料\FaceTraining_SIP_LIPS_20251102_190714.csv" # 10/10

    result = analyze_csv(CSV_PATH, cutoff=0.8, order=4, threshold=0.0008)

    print("\n===== 分析結果 =====")
    print(f"狀態: {result.get('status')}")
    print(f"動作次數: {result.get('action_count')}")
    print(f"總動作時間: {result.get('total_action_time')} 秒")
    print(f"\n斷點時間: {result.get('breakpoints', [])}")

    if result.get('status') == 'ERROR':
        print(f"\n❌ 錯誤: {result.get('error')}")
        if 'traceback' in result:
            print(f"\n完整錯誤:\n{result['traceback']}")

    if "debug" in result:
        debug = result["debug"]
        print(f"\n🔧 Debug Info:")
        print(f"  - FS: {debug.get('fs_hz')} Hz")
        print(f"  - Cutoff: {debug.get('cutoff')} Hz")
        print(f"  - E_demo: {debug.get('demo_E')}")
        print(f"  - E_thr: {debug.get('demo_E_thr')}")
        print(f"  - Energy threshold: {debug.get('energy_threshold')}")

    # 畫圖
    # debug_plot_last()
