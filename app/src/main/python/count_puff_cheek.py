"""
count_puff_cheek.py  —  PUFF CHEEK 動作計數（FFT Peak 版）
=============================================================
信號：a² (左右臉頰二次曲面 a² 係數之和)
計數：FFT-guided Peak Detection（方向感知 + DEMO 校正 + 面積篩選）

從 cheek_action_count_comparison.py (文件A) 正式化：
  - 只保留 FFT Peak 方法
  - 使用 ORIG 18+18 landmark
  - 加入面積篩選（移植自 count_reduce_cheek.py 文件B）
  - 回傳格式與 Java 端 CSVMotioner 一致
"""

import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt, find_peaks

try:
    import matplotlib.pyplot as plt
    HAS_PLOT = True
except ImportError:
    HAS_PLOT = False

# ╔══════════════════════════════════════════════════════════╗
# ║  Landmarks — ORIG 18+18（故意只用原始點）                 ║
# ╚══════════════════════════════════════════════════════════╝
LEFT_ORIG = [117, 118, 101, 36, 203, 212, 214, 192, 147, 123, 98, 97,
             164, 0, 37, 39, 40, 186]
RIGHT_ORIG = [164, 0, 267, 269, 270, 410, 423, 327, 326, 432, 434, 416,
              376, 352, 346, 347, 330, 266]

Z_SCALE = 1000.0

# ╔══════════════════════════════════════════════════════════╗
# ║  參數                                                    ║
# ╚══════════════════════════════════════════════════════════╝
FS_DEFAULT       = 10.0
ORDER            = 4
CUTOFF           = 1.0       # 低通濾波 cutoff (Hz) — PUFF 用 0.8
BASELINE_WIN_SEC = 7.0       # 移動平均窗口 (秒)
DEMO_SIDE_SEC    = 2.0       # DEMO 前後校正區寬度 (秒)

# FFT Peak 參數
FFT_MIN_FREQ = 0.05
FFT_MAX_FREQ = 1.0
DIST_FACTOR  = 0.6           # 最小峰間距 = 週期 × DIST_FACTOR
IQR_PROM     = 0.3           # prominence = IQR × IQR_PROM
DEMO_PROM    = 0.4           # prominence >= demo_amp × DEMO_PROM

# 面積篩選
DEMO_AREA_RATIO = 0.4      # 面積門檻 = DEMO 面積 × 此比例

# 時間驗證
MIN_ACTION_SEC = 0.3
MAX_ACTION_SEC = 6.0


# ╔══════════════════════════════════════════════════════════╗
# ║  信號提取（a² — PUFF CHEEK 專用）                         ║
# ╚══════════════════════════════════════════════════════════╝
def _get_anchor(row):
    """取 point0 與 point164 的中點作為 anchor"""
    try:
        ax = (float(row['point0_x']) + float(row['point164_x'])) / 2
        ay = (float(row['point0_y']) + float(row['point164_y'])) / 2
        az = ((float(row['point0_z']) + float(row['point164_z'])) / 2) * Z_SCALE
        return [ax, ay, az]
    except Exception:
        return [0, 0, 0]


def _get_points3d(row, idxs, anchor):
    """取 landmark 3D 座標（相對 anchor、z 放大）"""
    pts = []
    for k in idxs:
        try:
            x = float(row[f'point{k}_x']) - anchor[0]
            y = float(row[f'point{k}_y']) - anchor[1]
            z = float(row[f'point{k}_z']) * Z_SCALE - anchor[2]
            if np.isfinite(x) and np.isfinite(y) and np.isfinite(z):
                pts.append([x, y, z])
        except Exception:
            pass
    return np.array(pts, dtype=float) if pts else np.empty((0, 3))


def _fit_quadratic(x, y, z):
    """二次曲面擬合：z = a*x² + b*x*y + c*y² + d*x + e*y + f"""
    if len(x) < 6:
        return None
    A = np.column_stack([x*x, x*y, y*y, x, y, np.ones_like(x)])
    coef, *_ = np.linalg.lstsq(A, z, rcond=None)
    return coef


def compute_a2_signal(df):
    """
    計算 a² 信號（鼓臉 PUFF_CHEEK 用 coef[0]² = a²）
    擬合模型：z = a·x² + b·x·y + c·y² + d·x + e·y + f
      coef[0] = a（x² 係數）→ 鼓臉 PUFF 用這個
      coef[2] = c（y² 係數）→ 縮臉 REDUCE 用那個
    """
    n = len(df)
    signal = np.full(n, np.nan)
    for i in range(n):
        row = df.iloc[i]
        anc = _get_anchor(row)
        a_vals = []
        for idxs in [LEFT_ORIG, RIGHT_ORIG]:
            pts = _get_points3d(row, idxs, anc)
            if pts.shape[0] >= 6:
                coef = _fit_quadratic(pts[:, 0], pts[:, 1], pts[:, 2])
                if coef is not None:
                    a_vals.append(coef[0] ** 2)  # a²（鼓臉）
        if len(a_vals) == 2:
            signal[i] = a_vals[0] + a_vals[1]
    return signal


# ╔══════════════════════════════════════════════════════════╗
# ║  DSP 前處理                                              ║
# ╚══════════════════════════════════════════════════════════╝
def lowpass_filter(x, fs, cutoff=CUTOFF, order=ORDER):
    x = np.asarray(x, dtype=float)
    if x.size < 8:
        return x
    cutoff = min(cutoff, 0.49 * fs)
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    return filtfilt(b, a, x)


def moving_average(x, win_samples):
    win = max(1, min(int(win_samples), len(x) // 2))
    ker = np.ones(win) / win
    pad = win // 2
    xp = np.pad(x, pad, mode='edge')
    out = np.convolve(xp, ker, mode='same')
    return out[pad:-pad]


def calculate_fs_from_csv(file_path: str) -> float:
    """統計 MAINTAINING 段穩定區最低幀數作為 FS"""
    try:
        df = pd.read_csv(file_path)
        if "state" in df.columns:
            df = df[df["state"] == "MAINTAINING"]
        if len(df) < 2:
            return FS_DEFAULT
        t = pd.to_numeric(df["time_seconds"], errors="coerce").dropna().to_numpy()
        if t.size < 2:
            return FS_DEFAULT
        sec_counts = {}
        for ti in t:
            sec_counts[int(ti)] = sec_counts.get(int(ti), 0) + 1
        vals = list(sec_counts.values())
        if not vals:
            return FS_DEFAULT
        stable = vals[1:-1] if len(vals) > 2 else vals
        fs_est = float(min(stable)) if stable else FS_DEFAULT
        print(f"📊 Auto FS = {fs_est} Hz")
        return fs_est
    except Exception as e:
        print(f"⚠️ FS calculation error: {e}, using default {FS_DEFAULT}")
        return FS_DEFAULT


# ╔══════════════════════════════════════════════════════════╗
# ║  方向判斷 + DEMO 振幅                                    ║
# ╚══════════════════════════════════════════════════════════╝
def infer_direction_and_demo(t_all, r_filt_all, mask_demo):
    """
    從 DEMO 段推斷方向 + 取得 DEMO 振幅
    回傳: (direction, demo_amplitude)
    """
    if mask_demo is not None and mask_demo.sum() >= 3:
        idx = np.flatnonzero(mask_demo)
        tmin, tmax = t_all[idx[0]], t_all[idx[-1]]

        il = np.where((t_all >= tmin - DEMO_SIDE_SEC) & (t_all < tmin))[0]
        ir = np.where((t_all > tmax) & (t_all <= tmax + DEMO_SIDE_SEC))[0]

        sv = []
        if len(il) > 0:
            sv.extend(r_filt_all[il].tolist())
        if len(ir) > 0:
            sv.extend(r_filt_all[ir].tolist())

        if sv:
            baseline_val = float(np.mean(sv))
            r_demo = r_filt_all[idx]
            dm = float(np.mean(r_demo))

            if dm > baseline_val:
                direction = "P"
                peak = float(np.percentile(r_demo, 90))
            else:
                direction = "N"
                peak = float(np.percentile(r_demo, 10))

            demo_amp = abs(peak - baseline_val)
            print(f"[DIR] direction={direction}, demo_amp={demo_amp:.4e}")
            return direction, demo_amp

    print("[DIR] no valid DEMO -> fallback N, demo_amp=None")
    return "N", None


# ╔══════════════════════════════════════════════════════════╗
# ║  FFT Peak 計數（核心）                                    ║
# ╚══════════════════════════════════════════════════════════╝
def count_fft_peak(r_det, t, fs, dir_eff, demo_amp=None):
    """
    FFT-guided Peak Detection（方向感知 + DEMO 校正）

    1. 對 detrended 信號做 FFT，在 [FFT_MIN_FREQ, FFT_MAX_FREQ] 找主頻 f_dom
    2. 用 f_dom 算 distance（最小峰間距 = 週期 × DIST_FACTOR）
    3. 用 IQR 算 prominence，再跟 DEMO 振幅的 DEMO_PROM 倍取 max
    4. 呼叫 find_peaks() 得到峰數
    """
    sig = np.nan_to_num(r_det, nan=0)
    n = len(sig)
    if n < 8:
        return 0, [], {}

    # 方向處理：N 方向翻轉信號
    sig_for_fft = -sig if dir_eff == "N" else sig

    # FFT
    fft_mag = np.abs(np.fft.rfft(sig_for_fft))
    freqs = np.fft.rfftfreq(n, d=1.0 / fs)
    mask = (freqs >= FFT_MIN_FREQ) & (freqs <= FFT_MAX_FREQ)
    if not np.any(mask):
        return 0, [], {}

    fft_band = fft_mag.copy()
    fft_band[~mask] = 0
    idx_peak = np.argmax(fft_band)
    f_dom = freqs[idx_peak]
    if f_dom < 1e-6:
        return 0, [], {}

    # 用主頻算 distance
    period_samples = int(round(1.0 / f_dom * fs))
    dist = max(1, int(period_samples * DIST_FACTOR))

    # 用 IQR 算 prominence
    q75, q25 = np.percentile(sig_for_fft, [75, 25])
    prom = (q75 - q25) * IQR_PROM
    if prom < 1e-12:
        prom = np.std(sig_for_fft) * IQR_PROM

    # DEMO 校正
    if demo_amp is not None and demo_amp > 1e-12:
        prom = max(prom, demo_amp * DEMO_PROM)

    # 找峰
    peaks, props = find_peaks(sig_for_fft, prominence=prom, distance=dist)

    fft_info = {
        'f_dom': f_dom,
        'period': 1.0 / f_dom,
        'prom_threshold': prom,
        'distance': dist,
        'peak_count': len(peaks),
    }

    peak_times = t[peaks].tolist() if len(peaks) > 0 else []

    print(f"[FFT] f_dom={f_dom:.3f}Hz, period={1/f_dom:.1f}s, "
          f"dist={dist}, prom={prom:.2e}, peaks={len(peaks)}")

    return len(peaks), peak_times, fft_info


# ╔══════════════════════════════════════════════════════════╗
# ║  畫圖（電腦偵錯用）                                       ║
# ╚══════════════════════════════════════════════════════════╝
def plot_analysis(t, r_filt, baseline, r_det, t_all, r_all_filt,
                  mask_demo, dir_eff, fs, peak_times, fft_info, title=""):
    """畫出 FFT Peak 分析圖"""
    if not HAS_PLOT:
        return

    fig, axes = plt.subplots(3, 1, figsize=(16, 10))
    fig.suptitle(f'{title}\nFS={fs}Hz, Cutoff={CUTOFF}Hz, Dir={dir_eff}',
                 fontsize=12, fontweight='bold')

    # 子圖 1：全段信號 + baseline
    ax = axes[0]
    ax.plot(t_all, r_all_filt, 'gray', lw=0.8, alpha=0.5, label='Filtered (full)')
    ax.plot(t, r_filt, 'b-', lw=1, label='Filtered (MAIN)')
    ax.plot(t, baseline, 'r--', lw=1.5, label='Baseline')
    if mask_demo is not None and np.any(mask_demo):
        idx_d = np.flatnonzero(mask_demo)
        if idx_d.size > 0:
            ax.axvspan(t_all[idx_d[0]], t_all[idx_d[-1]], alpha=0.2, color='yellow', label='DEMO')
    ax.set_ylabel('a² signal')
    ax.set_title('Signal + Baseline')
    ax.legend(fontsize=7, loc='upper right')
    ax.grid(True, alpha=0.3)

    # 子圖 2：Detrended + peaks
    ax = axes[1]
    ax.plot(t, r_det, 'b-', lw=1, label='Detrended')
    ax.axhline(0, color='k', lw=0.5)
    for pt in peak_times:
        idx_close = np.argmin(np.abs(t - pt))
        ax.plot(pt, r_det[idx_close], 'm*', markersize=12, zorder=6)
    count = len(peak_times)
    ax.scatter([], [], c='magenta', s=80, marker='*', label=f'FFT Peak ({count})')
    ax.set_ylabel('Detrended')
    ax.set_title(f'FFT Peak count = {count}')
    ax.legend(fontsize=7, loc='upper right')
    ax.grid(True, alpha=0.3)

    # 子圖 3：FFT Spectrum
    ax = axes[2]
    if fft_info and 'f_dom' in fft_info:
        sig_for_fft = -np.nan_to_num(r_det, nan=0) if dir_eff == "N" else np.nan_to_num(r_det, nan=0)
        fft_mag = np.abs(np.fft.rfft(sig_for_fft))
        freqs = np.fft.rfftfreq(len(sig_for_fft), d=1.0 / fs)
        ax.plot(freqs, fft_mag, 'b-', lw=1)
        ax.axvspan(FFT_MIN_FREQ, FFT_MAX_FREQ, alpha=0.15, color='orange',
                   label=f'Search [{FFT_MIN_FREQ}-{FFT_MAX_FREQ}Hz]')
        f_dom = fft_info['f_dom']
        ax.axvline(f_dom, color='red', lw=2, ls='--',
                   label=f'f_dom={f_dom:.3f}Hz (T={1/f_dom:.1f}s)')
        ax.set_xlim(0, min(2.0, freqs[-1]))
        ax.set_xlabel('Frequency (Hz)')
        ax.set_ylabel('Magnitude')
        ax.set_title(f'FFT: f_dom={f_dom:.3f}Hz, prom>={fft_info["prom_threshold"]:.2e}')
        ax.legend(fontsize=7, loc='upper right')
        ax.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.show()
    return fig


# ╔══════════════════════════════════════════════════════════╗
# ║  主分析函數                                               ║
# ╚══════════════════════════════════════════════════════════╝
def analyze_csv(file_path: str, plot: bool = False) -> dict:
    """
    分析 PUFF CHEEK CSV — FFT Peak 版

    回傳格式與 count_reduce_cheek.py 一致（Java 端 CSVMotioner 不用改）：
    {
        "status": "OK" / "ERROR",
        "action_count": int,
        "total_action_time": float,
        "breakpoints": [float, ...],
        "segments": [{"index", "start_time", "end_time", "duration"}, ...],
        "debug": {...}
    }
    """
    try:
        df = pd.read_csv(file_path)
        if len(df) < 10:
            return {"status": "ERROR", "error": "資料太少 (< 10 rows)"}

        # ============================================================
        # 1. 全段信號（用於 DEMO 方向判斷）
        # ============================================================
        t_all_raw = pd.to_numeric(df["time_seconds"], errors="coerce").to_numpy()
        sig_all = compute_a2_signal(df)
        m_full = np.isfinite(t_all_raw) & np.isfinite(sig_all)
        t_all = t_all_raw[m_full]
        sig_all = sig_all[m_full]

        # 全段濾波（用於方向判斷）
        r_all_filt = lowpass_filter(sig_all, fs=FS_DEFAULT, cutoff=CUTOFF) \
            if len(sig_all) >= 8 else sig_all

        # DEMO mask
        mask_demo = None
        if "state" in df.columns:
            md = df["state"].astype(str).str.contains("DEMO", case=False, na=False).to_numpy()
            mask_demo = md[m_full]

        # 方向 + DEMO 振幅
        dir_eff, demo_amp = infer_direction_and_demo(t_all, r_all_filt, mask_demo)

        # ============================================================
        # 2. MAINTAINING 段信號
        # ============================================================
        if "state" in df.columns:
            df_main = df[df["state"].astype(str).str.contains(
                "MAINTAINING", case=False, na=False)].copy()
        else:
            df_main = df.copy()

        if len(df_main) < 10:
            return {
                "status": "OK",
                "action_count": 0,
                "total_action_time": 0.0,
                "breakpoints": [],
                "segments": [],
                "debug": {"note": "insufficient MAINTAINING data"}
            }

        t = pd.to_numeric(df_main["time_seconds"], errors="coerce").to_numpy()
        signal = compute_a2_signal(df_main)
        m = np.isfinite(t) & np.isfinite(signal)
        t, signal = t[m], signal[m]

        if t.size < 8:
            return {
                "status": "OK",
                "action_count": 0,
                "total_action_time": 0.0,
                "breakpoints": [],
                "segments": [],
                "debug": {"note": "insufficient valid data after filtering"}
            }

        # ============================================================
        # 3. 計算 FS + DSP 前處理
        # ============================================================
        fs = calculate_fs_from_csv(file_path)
        r_filt = lowpass_filter(signal, fs=fs, cutoff=CUTOFF)
        baseline = moving_average(r_filt, int(BASELINE_WIN_SEC * fs))
        r_det = r_filt - baseline

        # ============================================================
        # 4. FFT Peak 計數
        # ============================================================
        action_count, peak_times, fft_info = count_fft_peak(
            r_det, t, fs, dir_eff, demo_amp=demo_amp
        )

        # ============================================================
        # 4.5 計算 DEMO 面積（用於面積篩選門檻）
        # ============================================================
        _trapz = getattr(np, 'trapezoid', getattr(np, 'trapz', None))
        demo_area = 0.0

        if mask_demo is not None and mask_demo.sum() >= 3:
            baseline_all = moving_average(r_all_filt, int(BASELINE_WIN_SEC * FS_DEFAULT))
            r_all_det = r_all_filt - baseline_all
            sig_dir_all = -r_all_det if dir_eff == "N" else r_all_det
            idx_demo = np.flatnonzero(mask_demo)
            demo_seg = sig_dir_all[idx_demo]
            demo_t_seg = t_all[idx_demo]
            demo_area = float(_trapz(np.clip(demo_seg, 0, None), demo_t_seg)) \
                if len(demo_t_seg) > 1 else 0.0
            print(f"[DEMO AREA] = {demo_area:.2e}")

        # ============================================================
        # 5. 組裝 segments + 面積篩選
        # ============================================================
        area_threshold = demo_area * DEMO_AREA_RATIO if demo_area > 1e-12 else 0.0

        segments = []
        breakpoints = []

        if action_count > 0:
            sig_dir = -r_det if dir_eff == "N" else r_det

            # --- Pass 1: 算每個峰的半波面積 ---
            peak_data = []
            for i, pt in enumerate(peak_times):
                pk_idx = int(np.argmin(np.abs(t - pt)))

                # 往左找零交叉
                left_idx = pk_idx
                for j in range(pk_idx - 1, -1, -1):
                    if sig_dir[j] <= 0:
                        left_idx = j + 1
                        break
                else:
                    left_idx = 0

                # 往右找零交叉
                right_idx = pk_idx
                for j in range(pk_idx + 1, len(sig_dir)):
                    if sig_dir[j] <= 0:
                        right_idx = j
                        break
                else:
                    right_idx = len(sig_dir) - 1

                seg = sig_dir[left_idx:right_idx + 1]
                seg_t = t[left_idx:right_idx + 1]
                area = float(_trapz(np.clip(seg, 0, None), seg_t)) \
                    if len(seg_t) > 1 else 0.0

                peak_data.append({
                    "pt": pt, "pk_idx": pk_idx,
                    "left_idx": left_idx, "right_idx": right_idx,
                    "area": area,
                })

            # --- Pass 2: 面積篩選 ---
            print(f"[AREA] demo_area={demo_area:.2e}, "
                  f"threshold={area_threshold:.2e} (ratio={DEMO_AREA_RATIO})")

            for d in peak_data:
                if area_threshold > 0 and d["area"] < area_threshold:
                    print(f"  ❌ t={d['pt']:.1f}s area={d['area']:.2e} < {area_threshold:.2e}")
                    continue

                st = float(t[d["left_idx"]])
                ed = float(t[d["right_idx"]])

                # 避免與前一個 segment 重疊
                if len(segments) > 0 and st < segments[-1]["end_time"]:
                    st = segments[-1]["end_time"]

                duration = ed - st
                if duration < MIN_ACTION_SEC:
                    continue

                segments.append({
                    "index": len(segments),
                    "start_time": round(st, 3),
                    "end_time": round(ed, 3),
                    "duration": round(duration, 3),
                })
                breakpoints.append(round(ed, 3))

        action_count = len(segments)
        total_action_time = round(sum(s["duration"] for s in segments), 3)

        # ============================================================
        # 6. 畫圖（電腦偵錯用）
        # ============================================================
        if plot and HAS_PLOT:
            filename = file_path.split("\\")[-1].split("/")[-1]
            plot_analysis(
                t, r_filt, baseline, r_det,
                t_all, r_all_filt, mask_demo,
                dir_eff, fs, peak_times, fft_info,
                title=f"PUFF CHEEK: {filename}"
            )

        # ============================================================
        # 7. 回傳（格式與 count_reduce_cheek.py 一致）
        # ============================================================
        return {
            "status": "OK",
            "action_count": action_count,
            "total_action_time": total_action_time,
            "breakpoints": breakpoints,
            "segments": segments,
            "debug": {
                "fs_hz": fs,
                "cutoff": CUTOFF,
                "order": ORDER,
                "direction": dir_eff,
                "landmark_set": "ORIG",
                "demo_amp": round(demo_amp, 10) if demo_amp is not None else 0.0,
                "fft_f_dom": round(fft_info.get('f_dom', 0), 4),
                "fft_period": round(fft_info.get('period', 0), 3),
                "fft_prom": round(fft_info.get('prom_threshold', 0), 10),
                "fft_distance": fft_info.get('distance', 0),
                "fft_raw_peaks": fft_info.get('peak_count', 0),
                "demo_area": round(demo_area, 10),
                "area_threshold": round(area_threshold, 10),
                # 保留這些欄位給 Java 端讀（給 0 即可）
                "zc_all": 0,
                "zc_up": 0,
                "zc_down": 0,
                "deadband": 0.0,
                "min_interval": 0,
            }
        }

    except Exception as e:
        import traceback
        return {
            "status": "ERROR",
            "error": str(e),
            "traceback": traceback.format_exc()
        }


# ╔══════════════════════════════════════════════════════════╗
# ║  電腦端測試用                                             ║
# ╚══════════════════════════════════════════════════════════╝
if __name__ == "__main__":
    csv_files = [
        # ====== 把你的 CSV 路徑放這裡 ======
        # 3
        r"c:\Users\plus1\Downloads\Q_893b_FaceTraining_PUFF_CHEEK_20260315_164740.csv",
        # # 6
        r"C:\Users\plus1\Downloads\Q_893b_FaceTraining_PUFF_CHEEK_20260315_171138.csv",
        # # 3
        r"C:\Users\plus1\Downloads\Q_893b_FaceTraining_PUFF_CHEEK_20260315_171342.csv",
        # # 0
        r"C:\Users\plus1\Downloads\Q_893b_FaceTraining_PUFF_CHEEK_20260315_193131.csv",

        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251103_140759.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251103_153911.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251103_171540.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251103_171700.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251103_171829.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251104_035314.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251117_184536.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251117_184612.csv",
        r"C:\Users\plus1\Downloads\測試臉頰全點與外圍\FULL_CheeK_csv\PuffCheek\Training_PUFF_CHEEK_20251117_184733.csv",
    ]

    print("=" * 80)
    all_results = {}

    for i, csv_path in enumerate(csv_files, 1):
        filename = csv_path.split("\\")[-1]
        print(f"\n[{i}/{len(csv_files)}] 📁 {filename}")
        print("-" * 80)

        result = analyze_csv(csv_path, plot=False)
        all_results[filename] = result

        print(f"狀態: {result.get('status')}")
        print(f"動作次數: {result.get('action_count')}")
        print(f"總動作時間: {result.get('total_action_time')} 秒")

        if result.get('status') == 'ERROR':
            print(f"❌ 錯誤: {result.get('error')}")

    print("\n" + "=" * 80)
    print("📊 總結")
    print("=" * 80)

    for filename, result in all_results.items():
        count = result.get('action_count', 0)
        status = "✅" if result.get('status') == 'OK' else "❌"
        print(f"{filename:<70} {status} {count}")

    print("=" * 80)

    if HAS_PLOT:
        plt.show()