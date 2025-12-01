import math
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt, correlate, find_peaks

# === 自動偵測 matplotlib ===
try:
    import matplotlib.pyplot as plt
    HAS_PLOT = True
except ImportError:
    HAS_PLOT = False

# === cheek landmark sets ===
LEFT_CHEEK_IDXS  = [117,118,101,36,203,212,214,192,147,123,98,97,164,0,37,39,40,186]
RIGHT_CHEEK_IDXS = [164,0,267,269,270,410,423,327,326,432,434,416,376,352,346,347,330,266]

# === params ===
FS_DEFAULT         = 10.0
CUTOFF_DEFAULT     = 2.0   # 自動 cutoff 失敗時的備援值
ORDER              = 4
THRESHOLD          = 2e-6

# ==== 時間驗證參數（與 LIPS 統一）====
MIN_ACTION_DURATION = 0.3   # 最短動作時間（秒）
MAX_ACTION_DURATION = 6.0   # 最長動作時間（秒）

# ==== DEMO ===
K_DEMO_ENERGY  = 0.1  # 【棄用】 DEMO 能量比門檻的 K 值（E_thr = max(K*E_demo, E_noise)）
DEMO_SIDE_SEC  = 2.0   # DEMO 左右各幾秒當「校正」
alpha = 6.0   # 【棄用】 可調參數（能量比越大越嚴格）
R_DEMO = 0.4  # 能量門檻比例

# 〈同向波合併〉參數
MERGE_ENABLE          = True
BRIDGE_MAX_SEC        = 0.2
BRIDGE_MAX_RANGE_RATE = 0.6 #【棄用】


# ===== 自動計算 FS =====
def calculate_fs_from_csv(file_path: str) -> float:
    """
    統計CSV中 MAINTAINING 段,排除頭尾後的穩定區最低幀數作為FS
    """
    try:
        df = pd.read_csv(file_path)
        if "state" in df.columns:
            df = df[df["state"] == "MAINTAINING"]

        if len(df) < 2:
            return FS_DEFAULT

        t = pd.to_numeric(df["time_seconds"], errors="coerce").dropna().to_numpy()
        if t.size < 2:
            return FS_DEFAULT

        # 統計每秒的幀數
        sec_counts = {}
        for ti in t:
            sec = int(ti)
            sec_counts[sec] = sec_counts.get(sec, 0) + 1

        vals = list(sec_counts.values())
        if not vals:
            return FS_DEFAULT

        # 排除頭尾後取最小值
        stable = vals[1:-1] if len(vals) > 2 else vals
        fs_est = float(min(stable)) if stable else FS_DEFAULT
        print(f"📊 Auto FS = {fs_est} Hz")
        return fs_est
    except Exception as e:
        print(f"⚠️ FS calculation error: {e}, using default {FS_DEFAULT}")
        return FS_DEFAULT


# ===== 濾波 & 前處理 =====
def lowpass_filter(x, fs=FS_DEFAULT, cutoff=CUTOFF_DEFAULT, order=ORDER):
    x = np.asarray(x, dtype=float)
    if x.size < 8:
        return x
    # 保護：cutoff 不得 >= Nyquist
    cutoff = min(cutoff, 0.49 * fs)
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    return filtfilt(b, a, x)


def moving_average(x, win_samples):
    win = max(1, min(int(win_samples), len(x) // 2))
    ker = np.ones(win) / win
    pad = win // 2
    xpad = np.pad(x, pad, mode='edge')
    base = np.convolve(xpad, ker, mode='same')
    return base[pad:-pad]


# ===== 自動估 cutoff（核心功能）=====
def auto_cutoff_from_signal(t, r, fs,
                            min_period_sec=0.5,
                            max_period_sec=4.0,
                            gain_over_f0=1.5,
                            min_cut=0.25,
                            max_cut_cap=2.0
                            ):
    """
    用「實際 MAINTAINING 段」估主週期
    """
    x = np.asarray(r, dtype=float)
    if x.size < max(16, int(1.5*fs)):
        print("[AUTO-CUTOFF] data too short -> fallback cutoff")
        return CUTOFF_DEFAULT

    x_f = lowpass_filter(x, fs=fs, cutoff=min(1.5, 0.49*fs), order=ORDER)
    base = moving_average(x_f, int(max(3, 2.0 * fs)))
    xd = x_f - base

    std = np.std(xd)
    if std < 1e-12:
        print("[AUTO-CUTOFF] flat signal -> fallback cutoff")
        return CUTOFF_DEFAULT
    xn = (xd - np.mean(xd)) / std

    ac = correlate(xn, xn, mode='full')
    ac = ac[ac.size//2:]

    min_lag = int(min_period_sec * fs)
    max_lag = int(max_period_sec * fs)
    min_lag = max(min_lag, 1)
    max_lag = min(max_lag, ac.size-1)
    if max_lag <= min_lag:
        print("[AUTO-CUTOFF] bad lag window -> fallback cutoff")
        return CUTOFF_DEFAULT

    peaks, props = find_peaks(ac[min_lag:max_lag+1], prominence=0.05)
    if peaks.size > 0:
        lag = peaks[0] + min_lag
        period = lag / fs
        f0 = 1.0 / max(period, 1e-9)
        cutoff = gain_over_f0 * f0
        cutoff = float(np.clip(cutoff, min_cut, min(max_cut_cap, 0.49*fs)))
        cutoff = 1.0  # 固定使用 2.0
        return cutoff

    print(f"⚠️ [AUTO-CUTOFF] autocorr failed -> fallback cutoff={CUTOFF_DEFAULT:.2f}Hz")
    return CUTOFF_DEFAULT


# ===== 曲率計算 =====
def fit_quadratic_surface_xyz(x, y, z):
    if len(x) < 6:
        return (0., 0., 0., 0., 0., 0.)
    A = np.column_stack([x*x, x*y, y*y, x, y, np.ones_like(x)])
    coef, *_ = np.linalg.lstsq(A, z, rcond=None)
    return tuple(coef)


def curvature_proxy_from_quad(a, b, c):
    return math.sqrt((2*a)**2 + (2*c)**2 + 2*(b**2))


def cheek_patch_curvature(points3d):
    points3d = np.asarray(points3d, dtype=float)
    if points3d.shape[0] < 6:
        return 0.0
    x, y, z = points3d[:, 0], points3d[:, 1], points3d[:, 2]
    a, b, c, _, _, _ = fit_quadratic_surface_xyz(x, y, z)
    return curvature_proxy_from_quad(a, b, c)


def _row_points3d(row, idxs):
    pts = []
    for k in idxs:
        pts.append([
            float(row[f"point{k}_x"]),
            float(row[f"point{k}_y"]),
            float(row[f"point{k}_z"])
        ])
    return np.asarray(pts, dtype=float)


# ===== 零交叉 =====
def zero_crossings(x, min_interval, deadband=0.0):
    """
    回傳三個 list:
    - zc_all: 所有零交叉點
    - zc_up: 負→正的交叉點
    - zc_down: 正→負的交叉點
    """
    z_all, z_up, z_down = [], [], []
    last = -min_interval
    for i in range(1, len(x)):
        xi_1, xi = x[i-1], x[i]
        if np.isnan(xi_1) or np.isnan(xi):
            continue
        if xi_1 <= 0 and xi > 0 and abs(xi) > deadband:
            if i - last >= min_interval:
                z_all.append(i)
                z_up.append(i)
                last = i
        elif xi_1 >= 0 and xi < 0 and abs(xi) > deadband:
            if i - last >= min_interval:
                z_all.append(i)
                z_down.append(i)
                last = i
    return z_all, z_up, z_down


# ===== 方向判斷 (從 DEMO 段) =====
def infer_dir_from_demo_by_series(t_all, r_all, mask_demo, side_sec=2.0):
    """
    方向判斷（面積法）：
    - 取 DEMO 段前後各 side_sec 秒的平均值連成基準線
    - 比較 DEMO 段曲線相對基準線的上下面積
    - 上方面積 > 下方面積 → 'P'（往外鼓）
    - 否則 → 'N'（往內縮）
    """
    if mask_demo is None or mask_demo.sum() < 6:
        print("[DIR] DEMO too short -> fallback 'N'")
        return None

    idx_demo = np.flatnonzero(mask_demo)
    if idx_demo.size < 3:
        print("[DIR] DEMO too short -> fallback 'N'")
        return None

    tmin, tmax = t_all[idx_demo[0]], t_all[idx_demo[-1]]
    idx_left  = np.where((t_all >= tmin - side_sec) & (t_all < tmin))[0]
    idx_right = np.where((t_all > tmax) & (t_all <= tmax + side_sec))[0]

    if len(idx_left) == 0 or len(idx_right) == 0:
        print("[DIR] not enough side data -> fallback 'N'")
        return None

    r_left_avg  = np.mean(r_all[idx_left])
    r_right_avg = np.mean(r_all[idx_right])

    t_demo = t_all[idx_demo]
    r_demo = r_all[idx_demo]

    base = r_left_avg + (r_right_avg - r_left_avg) * (t_demo - t_demo[0]) / (t_demo[-1] - t_demo[0])
    diff = r_demo - base

    pos_area = np.trapz(np.clip(diff,  0, None), t_demo)
    neg_area = np.trapz(np.clip(-diff, 0, None), t_demo)
    d = "P" if pos_area > neg_area else "N"

    print(f"[DIR] Lavg={r_left_avg:.3e}, Ravg={r_right_avg:.3e}, pos={pos_area:.2e}, neg={neg_area:.2e} -> dir={d}")
    return d


# ===== 建立波段 =====
def build_segments_from_zc(zc_all, r_detrend, t):
    segs = []
    for i in range(len(zc_all) - 1):
        s_idx, e_idx = zc_all[i], zc_all[i+1]
        if e_idx <= s_idx:
            continue
        data = r_detrend[s_idx:e_idx]
        if data.size == 0:
            continue
        segs.append({
            "i": i,
            "s_idx": s_idx,
            "e_idx": e_idx,
            "st": float(t[s_idx]),
            "ed": float(t[e_idx]),
            "mean": float(np.mean(data)),
        })
    for s in segs:
        s["is_pos"] = (s["mean"] >= 0.0)
        s["dir"] = "P" if s["is_pos"] else "N"  # 與 LIPS 統一
    return segs


# ===== 合併同向波（與 LIPS 統一結構）=====
def merge_segments_by_time(segs, max_bridge_sec=BRIDGE_MAX_SEC):
    if not MERGE_ENABLE or len(segs) < 2:
        return segs

    changed = True
    while changed:
        changed = False
        out = []
        i = 0
        while i < len(segs):
            if i + 1 < len(segs):
                s0, s1 = segs[i], segs[i+1]
                gap = s1["st"] - s0["ed"]
                if s0["dir"] == s1["dir"] and gap <= max_bridge_sec:
                    merged = {
                        "i": s0.get("i", 0),
                        "s_idx": s0["s_idx"],
                        "e_idx": s1["e_idx"],
                        "st": s0["st"],
                        "ed": s1["ed"],
                        "duration": s1["ed"] - s0["st"],  # ← 重算 duration
                        "dir": s0["dir"],
                        "is_pos": s0.get("is_pos", s0["dir"] == "P"),  # ← 安全取值
                        "energy": s0.get("energy", 0) + s1.get("energy", 0),
                    }
                    # 保留極值
                    if "peak_val" in s0 or "peak_val" in s1:
                        merged["peak_val"] = max(s0.get("peak_val", -np.inf), s1.get("peak_val", -np.inf))
                        merged["peak_time"] = s0.get("peak_time") if s0.get("peak_val", -np.inf) >= s1.get("peak_val", -np.inf) else s1.get("peak_time")
                    if "trough_val" in s0 or "trough_val" in s1:
                        merged["trough_val"] = min(s0.get("trough_val", np.inf), s1.get("trough_val", np.inf))
                        merged["trough_time"] = s0.get("trough_time") if s0.get("trough_val", np.inf) <= s1.get("trough_val", np.inf) else s1.get("trough_time")

                    out.append(merged)
                    i += 2
                    changed = True
                    continue
            out.append(segs[i])
            i += 1
        segs = out
    return segs


# ===== DEMO 能量計算 =====
def compute_demo_features(t_all, r_all, mask_demo, fs, cutoff, dir_eff):
    """
    計算 DEMO 段的能量密度
    """
    if mask_demo is None or mask_demo.sum() < 3:
        return 0.0, 0.0

    idx_demo = np.flatnonzero(mask_demo)
    if idx_demo.size < 2:
        return 0.0, 0.0

    t_demo_start = float(t_all[idx_demo[0]])
    t_demo_end   = float(t_all[idx_demo[-1]])

    win_t0 = t_demo_start - DEMO_SIDE_SEC
    win_t1 = t_demo_end   + DEMO_SIDE_SEC

    mask_win = (t_all >= win_t0) & (t_all <= win_t1)
    if not np.any(mask_win):
        return 0.0, 0.0

    t_win = t_all[mask_win]
    r_win = r_all[mask_win]

    r_win_filt = lowpass_filter(r_win, fs=fs, cutoff=cutoff, order=ORDER)
    base_win   = moving_average(r_win_filt, int(7.0 * fs))
    r_win_det  = r_win_filt - base_win

    E_demo = energy_density_interval_dir(
        r_win_det, t_win, fs,
        t_demo_start, t_demo_end, dir_eff
    )

    energy_thr = R_DEMO * E_demo  # 使用 R_DEMO（與 LIPS 統一）
    return float(E_demo), float(energy_thr)


# ===== 能量密度計算 =====
def energy_density_interval_dir(x, t, fs, t0, t1, dir_eff):
    """
    計算指定方向的能量密度
    dir_eff = "P": 只計正值
    dir_eff = "N": 只計負值（取絕對值）
    """
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
    total = np.sum(vals) / fs
    dur = vals.size / fs
    return float(total / max(dur, 1e-9))


# ===== 分析高峰（P方向）=====
def analyze_high_peaks(r_detrend, t, fs, seg, energy_threshold, dir_eff="P"):
    """
    分析正半波（P方向）
    包含時間驗證（MIN/MAX_ACTION_DURATION）
    """
    spans = []
    s_idx, e_idx = seg["s_idx"], seg["e_idx"]
    seg_data = r_detrend[s_idx:e_idx]
    seg_t = t[s_idx:e_idx]

    if seg_data.size == 0:
        return spans

    # === 時間驗證 ===
    duration = seg_t[-1] - seg_t[0]
    if duration < MIN_ACTION_DURATION:
        return spans
    if duration > MAX_ACTION_DURATION:
        return spans

    # 計算能量密度
    seg_energy = energy_density_interval_dir(
        seg_data, seg_t, fs,
        seg_t[0], seg_t[-1],
        dir_eff
    )

    if seg_energy >= energy_threshold:
        peak_val = np.max(seg_data)
        peak_idx = np.argmax(seg_data)
        peak_time = t[s_idx + peak_idx]

        spans.append({
            "type": "P",
            "dir": "P",
            "i": seg["i"],
            "is_pos": True,
            "s_idx": s_idx,
            "e_idx": e_idx,
            "st": float(seg_t[0]),
            "ed": float(seg_t[-1]),
            "duration": float(duration),
            "energy": float(seg_energy),
            "energy_thr": float(energy_threshold),
            "peak_time": float(peak_time),
            "peak_val": float(peak_val),
        })

    return spans


# ===== 分析低谷（N方向）=====
def analyze_low_troughs(r_detrend, t, fs, seg, energy_threshold, dir_eff="N"):
    """
    分析負半波（N方向）
    包含時間驗證（MIN/MAX_ACTION_DURATION）
    """
    spans = []
    s_idx, e_idx = seg["s_idx"], seg["e_idx"]
    seg_data = r_detrend[s_idx:e_idx]
    seg_t = t[s_idx:e_idx]

    if seg_data.size == 0:
        return spans

    # === 時間驗證（之前 LIPS 的 bug 修復）===
    duration = seg_t[-1] - seg_t[0]
    if duration < MIN_ACTION_DURATION:
        return spans
    if duration > MAX_ACTION_DURATION:
        return spans

    # 計算能量密度
    seg_energy = energy_density_interval_dir(
        seg_data, seg_t, fs,
        seg_t[0], seg_t[-1],
        dir_eff
    )

    if seg_energy >= energy_threshold:
        trough_val = np.min(seg_data)
        trough_idx = np.argmin(seg_data)
        trough_time = t[s_idx + trough_idx]

        spans.append({
            "type": "N",
            "dir": "N",
            "i": seg["i"],
            "is_pos": False,
            "s_idx": s_idx,
            "e_idx": e_idx,
            "st": float(seg_t[0]),
            "ed": float(seg_t[-1]),
            "duration": float(duration),
            "energy": float(seg_energy),
            "energy_thr": float(energy_threshold),
            "trough_time": float(trough_time),
            "trough_val": float(trough_val),
        })

    return spans
def plot_analysis(t, r_raw, r_filt, baseline, r_detrend,
                  zc_all, zc_up, zc_down,
                  segments, action_spans,
                  dir_eff, demo_energy, energy_threshold,
                  t_all, r_all, mask_demo,
                  fs, cutoff, title="CHEEK Analysis"):
    """
    畫出完整分析圖（包含 DEMO 校正區）

    子圖配置：
    1. 全段原始信號 + DEMO 區間標記
    2. MAINTAINING 濾波後信號 + baseline
    3. Detrend信號 + 零交叉點 + 動作標記
    4. 能量分析（各段能量 vs 門檻）
    """
    if not HAS_PLOT:
        return

    fig, axes = plt.subplots(4, 1, figsize=(14, 12))
    fig.suptitle(f"{title}\nDir={dir_eff}, FS={fs:.1f}Hz, Cutoff={cutoff:.2f}Hz", fontsize=12)

    # ===== 子圖1: 全段原始信號（包含 DEMO）=====
    ax1 = axes[0]
    ax1.plot(t_all, r_all, 'b-', alpha=0.6, label='Raw Curvature (Full)', linewidth=0.8)

    # 標記 DEMO 區間
    if mask_demo is not None and np.any(mask_demo):
        idx_demo = np.flatnonzero(mask_demo)
        if idx_demo.size > 0:
            demo_start = t_all[idx_demo[0]]
            demo_end = t_all[idx_demo[-1]]
            ax1.axvspan(demo_start, demo_end, alpha=0.3, color='yellow', label='DEMO')

            # 標記校正區（DEMO 前後 DEMO_SIDE_SEC 秒）
            ax1.axvspan(demo_start - DEMO_SIDE_SEC, demo_start,
                       alpha=0.2, color='orange', label='Left Calib')
            ax1.axvspan(demo_end, demo_end + DEMO_SIDE_SEC,
                       alpha=0.2, color='orange', label='Right Calib')

    # 標記 MAINTAINING 區間
    if len(t) > 0:
        ax1.axvspan(t[0], t[-1], alpha=0.1, color='green', label='MAINTAINING')

    ax1.set_ylabel('Curvature')
    ax1.set_title('Full Signal (with DEMO + Calibration Zones)')
    ax1.legend(loc='upper right', fontsize=8)
    ax1.grid(True, alpha=0.3)

    # ===== 子圖2: MAINTAINING 濾波後信號 =====
    ax2 = axes[1]
    ax2.plot(t, r_filt, 'g-', label='Filtered', linewidth=1)
    ax2.plot(t, baseline, 'r--', label='Baseline', linewidth=1.5)
    ax2.set_ylabel('Curvature')
    ax2.set_title('Filtered Signal + Baseline (MAINTAINING only)')
    ax2.legend(loc='upper right')
    ax2.grid(True, alpha=0.3)

    # ===== 子圖3: Detrend + 零交叉 + 動作標記 =====
    ax3 = axes[2]
    ax3.plot(t, r_detrend, 'b-', label='Detrended', linewidth=1)
    ax3.axhline(y=0, color='k', linestyle='-', linewidth=0.5)

    # 零交叉點
    if len(zc_up) > 0:
        ax3.scatter(t[zc_up], r_detrend[zc_up], c='green', s=30, marker='^', label='ZC Up', zorder=5)
    if len(zc_down) > 0:
        ax3.scatter(t[zc_down], r_detrend[zc_down], c='red', s=30, marker='v', label='ZC Down', zorder=5)

    # 標記有效動作段
    for i, span in enumerate(action_spans):
        color = 'lime' if span["dir"] == "P" else 'cyan'
        ax3.axvspan(span["st"], span["ed"], alpha=0.3, color=color)

        # 標記峰/谷
        if "peak_time" in span:
            ax3.scatter([span["peak_time"]], [span["peak_val"]],
                       c='red', s=100, marker='*', zorder=10)
            ax3.annotate(f'#{i+1}', (span["peak_time"], span["peak_val"]),
                        textcoords="offset points", xytext=(0, 10), ha='center', fontsize=8)
        if "trough_time" in span:
            ax3.scatter([span["trough_time"]], [span["trough_val"]],
                       c='blue', s=100, marker='*', zorder=10)
            ax3.annotate(f'#{i+1}', (span["trough_time"], span["trough_val"]),
                        textcoords="offset points", xytext=(0, -15), ha='center', fontsize=8)

    ax3.set_ylabel('Detrended')
    ax3.set_title(f'Detrended Signal + Actions (Count={len(action_spans)})')
    ax3.legend(loc='upper right')
    ax3.grid(True, alpha=0.3)

    # ===== 子圖4: 能量分析 =====
    ax4 = axes[3]

    seg_centers = []
    seg_energies = []
    seg_colors = []

    for seg in segments:
        center = (seg["st"] + seg["ed"]) / 2
        energy = seg.get("energy", 0)
        seg_centers.append(center)
        seg_energies.append(energy)

        is_action = any(
            (span["st"] == seg["st"] and span["ed"] == seg["ed"])
            for span in action_spans
        )
        if is_action:
            seg_colors.append('lime' if seg["dir"] == "P" else 'cyan')
        else:
            seg_colors.append('gray')

    if seg_centers:
        bar_width = np.min(np.diff(seg_centers)) * 0.8 if len(seg_centers) > 1 else 0.5
        ax4.bar(seg_centers, seg_energies, width=bar_width, color=seg_colors, alpha=0.7, edgecolor='black')

    ax4.axhline(y=energy_threshold, color='red', linestyle='--', linewidth=2, label=f'Threshold={energy_threshold:.2e}')
    ax4.axhline(y=demo_energy, color='orange', linestyle=':', linewidth=2, label=f'DEMO Energy={demo_energy:.2e}')

    ax4.set_xlabel('Time (s)')
    ax4.set_ylabel('Energy Density')
    ax4.set_title('Segment Energy Analysis')
    ax4.legend(loc='upper right')
    ax4.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.show()

    return fig


# ===== 主分析函數（電腦版，帶畫圖）=====
def analyze_csv(file_path: str, plot: bool = True) -> dict:
    """
    分析 CHEEK CSV 檔案（電腦版，帶畫圖功能）

    參數:
        file_path: CSV 檔案路徑
        plot: 是否畫圖（預設 True）

    回傳:
        dict 格式的分析結果
    """
    try:
        df = pd.read_csv(file_path)
        if len(df) < 10:
            return {"status": "ERROR", "error": "資料太少 (< 10 rows)"}

        # === 全段 raw (用於判斷方向) ===
        t_all = pd.to_numeric(df["time_seconds"], errors="coerce").to_numpy()
        curv_all = []
        for _, row in df.iterrows():
            PL = _row_points3d(row, LEFT_CHEEK_IDXS)
            PR = _row_points3d(row, RIGHT_CHEEK_IDXS)
            curv_all.append(cheek_patch_curvature(PL) + cheek_patch_curvature(PR))
        r_all = np.asarray(curv_all, dtype=float)

        m_all = np.isfinite(t_all) & np.isfinite(r_all)
        t_all, r_all = t_all[m_all], r_all[m_all]

        # === 提取 DEMO mask ===
        mask_demo = None
        if "state" in df.columns:
            s = df["state"].astype(str)
            mask_demo_all = s.str.contains("DEMO", case=False, na=False).to_numpy()
            mask_demo = mask_demo_all[m_all]

        # === 方向判斷 ===
        dir_eff = infer_dir_from_demo_by_series(t_all, r_all, mask_demo) or "N"
        print(f"[DIR] using = {dir_eff}")

        # === 僅對 MAINTAINING 處理 ===
        if "state" in df.columns:
            df_main = df[df["state"].astype(str).str.contains("MAINTAINING", case=False, na=False)].copy()
        else:
            df_main = df.copy()

        t = pd.to_numeric(df_main["time_seconds"], errors="coerce").to_numpy()
        curv_main = []
        for _, row in df_main.iterrows():
            PL = _row_points3d(row, LEFT_CHEEK_IDXS)
            PR = _row_points3d(row, RIGHT_CHEEK_IDXS)
            curv_main.append(cheek_patch_curvature(PL) + cheek_patch_curvature(PR))
        r = np.asarray(curv_main, dtype=float)

        m = np.isfinite(t) & np.isfinite(r)
        t, r = t[m], r[m]

        if t.size < 2:
            return {
                "status": "OK",
                "action_count": 0,
                "total_action_time": 0.0,
                "breakpoints": [],
                "segments": [],
                "curve": [],
                "debug": {"note": "insufficient MAINTAINING data"}
            }

        # === 自動計算 FS ===
        fs = calculate_fs_from_csv(file_path)

        # === 自動計算 cutoff ===
        cutoff_auto = auto_cutoff_from_signal(t, r, fs)

        # === 濾波/基線/去趨勢 ===
        r_filt = lowpass_filter(r, fs=fs, cutoff=cutoff_auto, order=ORDER)
        baseline = moving_average(r_filt, int(7.0 * fs))
        r_detrend = r_filt - baseline

        # === 零交叉 ===
        std = float(np.std(r_detrend)) if len(r_detrend) else 0.0
        deadband = 0.000 * std if std > 0 else 0.0
        min_interval = int(0.5 * fs)  # 與 LIPS 統一
        zc_all, zc_up, zc_down = zero_crossings(r_detrend, min_interval, deadband)

        # === 建段 ===
        segments = build_segments_from_zc(zc_all, r_detrend, t)

        # === DEMO 能量 ===
        demo_energy, energy_threshold = compute_demo_features(
            t_all, r_all, mask_demo, fs, cutoff_auto, dir_eff
        )
        print(f"[ENERGY] DEMO={demo_energy:.2e}, Threshold={energy_threshold:.2e}")

        # === 分析各段，計算能量並標記動作 ===
        action_spans = []
        pos_waves = neg_waves = 0

        for seg in segments:
            s_idx, e_idx = seg["s_idx"], seg["e_idx"]
            seg_data = r_detrend[s_idx:e_idx]
            seg_t = t[s_idx:e_idx]

            if seg_data.size == 0:
                continue

            # 計算該段能量
            seg_energy = energy_density_interval_dir(
                seg_data, seg_t, fs,
                seg_t[0], seg_t[-1],
                seg["dir"]
            )
            seg["energy"] = seg_energy

            if seg["is_pos"]:
                pos_waves += 1
            else:
                neg_waves += 1

            # P 方向分析
            if dir_eff == "P" and seg["is_pos"]:
                spans = analyze_high_peaks(r_detrend, t, fs, seg, energy_threshold, "P")
                action_spans.extend(spans)

            # N 方向分析
            if dir_eff == "N" and not seg["is_pos"]:
                spans = analyze_low_troughs(r_detrend, t, fs, seg, energy_threshold, "N")
                action_spans.extend(spans)

        # === 合併（可選）===
        action_spans = merge_segments_by_time(action_spans, BRIDGE_MAX_SEC)

        # === 統計 ===
        action_count = len(action_spans)
        total_action_time = sum(span["duration"] for span in action_spans)

        # === 畫圖 ===
        if plot and HAS_PLOT:
            filename = file_path.split("\\")[-1].split("/")[-1]
            plot_analysis(
                t, r, r_filt, baseline, r_detrend,
                zc_all, zc_up, zc_down,
                segments, action_spans,
                dir_eff, demo_energy, energy_threshold,
                t_all, r_all, mask_demo,
                fs, cutoff_auto,
                title=f"CHEEK: {filename}"
            )

        # === 輸出 ===
        breakpoints = [round(span["ed"], 3) for span in action_spans]

        return {
            "status": "OK",
            "action_count": action_count,
            "total_action_time": round(total_action_time, 3),
            "breakpoints": breakpoints,
            "segments": [
                {
                    "index": i,
                    "start_time": round(span["st"], 3),
                    "end_time": round(span["ed"], 3),
                    "duration": round(span["duration"], 3),
                    "energy": round(span["energy"], 10),
                }
                for i, span in enumerate(action_spans)
            ],
            "debug": {
                "fs_hz": fs,
                "cutoff": round(cutoff_auto, 3),
                "order": ORDER,
                "direction": dir_eff,
                "demo_energy": round(demo_energy, 10),
                "energy_threshold": round(energy_threshold, 10),
                "total_segments": len(segments),
                "pos_waves": pos_waves,
                "neg_waves": neg_waves,
                "zc_all": len(zc_all),
                "zc_up": len(zc_up),
                "zc_down": len(zc_down),
                "deadband": deadband,
                "min_interval": min_interval,
                "min_action_duration": MIN_ACTION_DURATION,
                "max_action_duration": MAX_ACTION_DURATION,
            }
        }

    except Exception as e:
        import traceback
        return {
            "status": "ERROR",
            "error": str(e),
            "traceback": traceback.format_exc()
        }


# ===== 測試用（保留原始註解）=====
# if __name__ == "__main__":
#     # 測試範例
#     # CSV_PATH = r"C:\Users\plus1\Downloads\FaceTraining_PUFF_CHEEK_20251030_160710_4變5.csv"
#     # CSV_PATH 換成你自己要測的檔案
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\FaceTraining_PUFF_CHEEK_20251103_171829_Anw3.csv"
#     CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\FaceTraining_PUFF_CHEEK_20251103_153910_shein_REDO.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\FaceTraining_PUFF_CHEEK_20251103_140758_SUM.csv"


#     # === 十月底 PUFF CHEEK CSV 測試檔案  ，沒DEMO ===
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251027_132845_講話臉頰9.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251027_132937_臉頰_呼吸8次.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251027_133220_臉頰_脫眼鏡9_呼吸.csv"


#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251027_133322_臉頰_4變7.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251029_135550_藍色框_鼓臉頰.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251029_152616_4便6.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251029_155036_便0.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251030_160710_4變5.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251030_192930_4便3.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251030_195305_6變5.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251030_201854.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251031_124339_+X變成2_真的大客可能是硬....csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_PUFF_CHEEK_20251031_124504_9變4_CUTTOFF關係.csv"

#     # === 十月底 REDUCE CHEEK CSV 測試檔案 ===
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_REDUCE_CHEEK_20251030_201935.csv"

#     #感覺是兩段 縮完股回來有點像股臉
#     #DEMO廢掉
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_REDUCE_CHEEK_20251031_122842_5變4.csv"

#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_REDUCE_CHEEK_20251031_123535_5變5.csv"
#     # CSV_PATH = r"C:\Users\plus1\OneDrive\Desktop\0519\測試區\0918_meeting\sim_debug_plots\REALcsv\十月底\csv\FaceTraining_REDUCE_CHEEK_20251031_155323_S24_10變1.csv"


#     #6/6
#     #4/4
#     #7/7
#     #8/8
#     #10/10


#     test_file = CSV_PATH
#     result = analyze_csv(test_file)

#     print("\n" + "="*60)
#     print("📊 Analysis Result (with AUTO CUTOFF):")
#     print("="*60)
#     print(f"Status: {result.get('status')}")
#     print(f"Action Count: {result.get('action_count')}")
#     print(f"Total Action Time: {result.get('total_action_time')}s")
#     print(f"Total Segments: {len(result.get('segments', []))}")
#     print(f"Breakpoints: {len(result.get('breakpoints', []))}")

#     if 'debug' in result:
#         debug = result['debug']
#         print(f"\n🔧 Debug Info:")
#         print(f"  - FS: {debug.get('fs_hz')} Hz")
#         print(f"  - Auto Cutoff: {debug.get('cutoff_auto')} Hz")
#         print(f"  - Direction: {debug.get('direction')}")
#         print(f"  - Pos Waves: {debug.get('pos_waves')}")
#         print(f"  - Neg Waves: {debug.get('neg_waves')}")

#     print("\n" + "="*60)


if __name__ == "__main__":
    # 直接列出所有要分析的檔案
    # PUFF
    csv_files = [

r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251117_184731.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251117_184611.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251117_184535.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251104_035313.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251103_171829.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251103_171659.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251103_171539.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251103_153910.csv",
r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\PUFF_CHEEK\FaceTraining_PUFF_CHEEK_20251103_140758.csv",
 r"C:\Users\plus1\Downloads\user01_FaceTraining_PUFF_CHEEK_20251201_154622.csv",
 r"C:\Users\plus1\Downloads\testuser01_FaceTraining_PUFF_CHEEK_20251201_160825_3"
    ]

# REDUCE
    # csv_files = [
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251117_190115.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251117_190036.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251117_185937.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251117_185857.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251104_035420.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251103_175709.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251103_171947.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251103_155009.csv",
    # r"C:\Users\plus1\Downloads\1118從S24取出資料_分類\REDUCE_CHEEK\FaceTraining_REDUCE_CHEEK_20251103_140909.csv"
    # ]


    print("=" * 80)
    all_results = {}

    for i, csv_path in enumerate(csv_files, 1):
        filename = csv_path.split("\\")[-1]

        print(f"\n[{i}/{len(csv_files)}] 📁 {filename}")
        print("-" * 80)

        result = analyze_csv(csv_path, plot=False)  # plot=True 開啟畫圖
        all_results[filename] = result

        print(f"狀態: {result.get('status')}")
        print(f"動作次數: {result.get('action_count')}")
        print(f"總動作時間: {result.get('total_action_time')} 秒")

        if result.get('status') == 'ERROR':
            print(f"❌ 錯誤: {result.get('error')}")

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