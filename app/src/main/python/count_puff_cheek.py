"""
臉頰動作計數 (APP 版) — 特徵改用「二面角」
=============================================
原版特徵：左右臉頰曲率 (curvature proxy) 加總
新版特徵：左右臉頰對齊後，在曲面交線處計算二面角 (dihedral angle)

【改動說明】
1. 新增 align_points()          — 用 point0 / point164 對齊左右臉頰
2. 新增 dihedral_at_intersection() — 在兩曲面交線上算法向量夾角
3. 新增常數 IDX_P164_IN_LEFT / IDX_P0_IN_LEFT
4. cheek_patch_curvature / curvature_proxy_from_quad 已移除（不再需要）
5. 所有呼叫特徵計算的地方，從 curvature 改成 dihedral
6. 其餘 pipeline（濾波、去趨勢、零交叉、能量密度、DEMO、合併）完全不動
7. 【新改動】compute_demo_features 改用全段 detrend（不再用 ±2s window）

用法與原版相同：
    result = analyze_csv("your_file.csv")
"""

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

# ============================================================
# cheek landmark sets（與原版相同）
# ============================================================
LEFT_CHEEK_IDXS  = [117,118,101,36,203,212,214,192,147,123,98,97,164,0,37,39,40,186]
RIGHT_CHEEK_IDXS = [164,0,267,269,270,410,423,327,326,432,434,416,376,352,346,347,330,266]

# ============================================================
# 【新增】對齊用的 index：point164 和 point0 在 LEFT_CHEEK_IDXS 中的位置
# ============================================================
IDX_P164_IN_LEFT = LEFT_CHEEK_IDXS.index(164)  # = 12
IDX_P0_IN_LEFT   = LEFT_CHEEK_IDXS.index(0)    # = 13

# ============================================================
# params（與原版相同）
# ============================================================
FS_DEFAULT         = 10.0
CUTOFF_DEFAULT     = 2.0
ORDER              = 4
THRESHOLD          = 2e-6

MIN_ACTION_DURATION = 1.0
MAX_ACTION_DURATION = 6.0

K_DEMO_ENERGY  = 0.1   # 【棄用】
DEMO_SIDE_SEC  = 2.0
alpha = 6.0            # 【棄用】
R_DEMO = 0.4

MERGE_ENABLE          = True
BRIDGE_MAX_SEC        = 0.2
BRIDGE_MAX_RANGE_RATE = 0.6  # 【棄用】


# ============================================================
# 共用工具（與原版相同）
# ============================================================

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
            sec = int(ti)
            sec_counts[sec] = sec_counts.get(sec, 0) + 1
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


def lowpass_filter(x, fs=FS_DEFAULT, cutoff=CUTOFF_DEFAULT, order=ORDER):
    """低通濾波器"""
    x = np.asarray(x, dtype=float)
    if x.size < 8:
        return x
    cutoff = min(cutoff, 0.49 * fs)
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    return filtfilt(b, a, x)


def moving_average(x, win_samples):
    """移動平均（基線估計）"""
    win = max(1, min(int(win_samples), len(x) // 2))
    ker = np.ones(win) / win
    pad = win // 2
    xpad = np.pad(x, pad, mode='edge')
    base = np.convolve(xpad, ker, mode='same')
    return base[pad:-pad]


def auto_cutoff_from_signal(t, r, fs,
                            min_period_sec=0.5,
                            max_period_sec=4.0,
                            gain_over_f0=1.5,
                            min_cut=0.25,
                            max_cut_cap=2.0):
    """用自相關估主週期，決定 cutoff（目前固定回傳 0.5）"""
    x = np.asarray(r, dtype=float)
    if x.size < max(16, int(1.5 * fs)):
        print("[AUTO-CUTOFF] data too short -> fallback cutoff")
        return CUTOFF_DEFAULT

    x_f = lowpass_filter(x, fs=fs, cutoff=min(1.5, 0.49 * fs), order=ORDER)
    base = moving_average(x_f, int(max(3, 2.0 * fs)))
    xd = x_f - base

    std = np.std(xd)
    if std < 1e-12:
        print("[AUTO-CUTOFF] flat signal -> fallback cutoff")
        return CUTOFF_DEFAULT
    xn = (xd - np.mean(xd)) / std

    ac = correlate(xn, xn, mode='full')
    ac = ac[ac.size // 2:]

    min_lag = max(int(min_period_sec * fs), 1)
    max_lag = min(int(max_period_sec * fs), ac.size - 1)
    if max_lag <= min_lag:
        print("[AUTO-CUTOFF] bad lag window -> fallback cutoff")
        return CUTOFF_DEFAULT

    peaks, _ = find_peaks(ac[min_lag:max_lag + 1], prominence=0.05)
    if peaks.size > 0:
        cutoff = 0.5  # 固定使用 0.5Hz
        return cutoff

    print(f"⚠️ [AUTO-CUTOFF] autocorr failed -> fallback cutoff={CUTOFF_DEFAULT:.2f}Hz")
    return CUTOFF_DEFAULT


# ============================================================
# 二次曲面擬合（與原版相同，二面角也需要用到）
# ============================================================

def fit_quadratic_surface_xyz(x, y, z):
    """擬合 z = ax² + bxy + cy² + dx + ey + f，回傳 (a,b,c,d,e,f)"""
    if len(x) < 6:
        return (0., 0., 0., 0., 0., 0.)
    A = np.column_stack([x * x, x * y, y * y, x, y, np.ones_like(x)])
    coef, *_ = np.linalg.lstsq(A, z, rcond=None)
    return tuple(coef)


# ============================================================
# 【新增】對齊函數 — 用 point0 / point164 將左右臉頰對齊到同一座標系
# ============================================================

def align_points(lp, rp):
    """
    對齊左右臉頰 landmarks：
    1. 以 point0 和 point164 的中點為原點平移
    2. 以 point164→point0 方向為基準旋轉（消除頭部轉動影響）
    """
    p0   = lp[IDX_P0_IN_LEFT]
    p164 = lp[IDX_P164_IN_LEFT]

    center = (p0 + p164) / 2
    lp = lp - center
    rp = rp - center

    direction = p164 - p0
    angle = np.arctan2(direction[0], direction[1])
    cos_a, sin_a = np.cos(-angle), np.sin(-angle)
    rot = np.array([
        [cos_a, -sin_a, 0],
        [sin_a,  cos_a, 0],
        [0,      0,     1]
    ])
    return lp @ rot.T, rp @ rot.T


# ============================================================
# 【新增】二面角計算 — 在左右曲面交線處算法向量夾角
# ============================================================

def dihedral_at_intersection(lc, rc, lp, rp):
    """
    計算左右臉頰曲面在交線處的二面角（度數）。
    """
    diff = np.array(lc) - np.array(rc)

    all_pts = np.vstack([lp, rp])
    xmin, xmax = all_pts[:, 0].min(), all_pts[:, 0].max()
    ymin, ymax = all_pts[:, 1].min(), all_pts[:, 1].max()

    n = 60
    xl = np.linspace(xmin, xmax, n)
    yl = np.linspace(ymin, ymax, n)
    X, Y = np.meshgrid(xl, yl)

    a, b, c, d, e, f = diff
    Zd = a * X * X + b * X * Y + c * Y * Y + d * X + e * Y + f

    cxs, cys = [], []
    for i in range(n - 1):
        for j in range(n - 1):
            vals = [Zd[i, j], Zd[i, j + 1], Zd[i + 1, j], Zd[i + 1, j + 1]]
            if min(vals) <= 0 and max(vals) >= 0:
                cxs.append(X[i, j] + (xl[1] - xl[0]) / 2)
                cys.append(Y[i, j] + (yl[1] - yl[0]) / 2)

    angles = []
    if cxs:
        step = max(1, len(cxs) // 20)
        for idx in range(0, len(cxs), step):
            px, py = cxs[idx], cys[idx]

            glx = 2 * lc[0] * px + lc[1] * py + lc[3]
            gly = lc[1] * px + 2 * lc[2] * py + lc[4]
            nL = np.array([-glx, -gly, 1.0])

            grx = 2 * rc[0] * px + rc[1] * py + rc[3]
            gry = rc[1] * px + 2 * rc[2] * py + rc[4]
            nR = np.array([-grx, -gry, 1.0])

            cos_a = np.dot(nL, nR) / (np.linalg.norm(nL) * np.linalg.norm(nR) + 1e-20)
            angles.append(np.arccos(np.clip(cos_a, -1, 1)) * 180 / np.pi)

    return np.mean(angles) if angles else 0.0


# ============================================================
# 讀取 3D landmarks（與原版相同）
# ============================================================

def _row_points3d(row, idxs):
    pts = []
    for k in idxs:
        pts.append([
            float(row[f"point{k}_x"]),
            float(row[f"point{k}_y"]),
            float(row[f"point{k}_z"])
        ])
    return np.asarray(pts, dtype=float)


# ============================================================
# 【新增】計算每幀的二面角序列（取代原本的曲率序列）
# ============================================================

def compute_dihedral_series(df):
    vals = []
    for _, row in df.iterrows():
        lp = _row_points3d(row, LEFT_CHEEK_IDXS)
        rp = _row_points3d(row, RIGHT_CHEEK_IDXS)

        lp, rp = align_points(lp, rp)

        x_l, y_l, z_l = lp[:, 0], lp[:, 1], lp[:, 2]
        x_r, y_r, z_r = rp[:, 0], rp[:, 1], rp[:, 2]
        lc = fit_quadratic_surface_xyz(x_l, y_l, z_l)
        rc = fit_quadratic_surface_xyz(x_r, y_r, z_r)

        vals.append(dihedral_at_intersection(lc, rc, lp, rp))

    return np.asarray(vals, dtype=float)


# ============================================================
# 零交叉（與原版相同）
# ============================================================

def zero_crossings(x, min_interval, deadband=0.0):
    z_all, z_up, z_down = [], [], []
    last = -min_interval
    for i in range(1, len(x)):
        xi_1, xi = x[i - 1], x[i]
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


# ============================================================
# 方向判斷（與原版相同）
# ============================================================

def infer_dir_from_demo_by_series(t_all, r_all, mask_demo, side_sec=2.0):
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


# ============================================================
# 建段（與原版相同）
# ============================================================

def build_segments_from_zc(zc_all, r_detrend, t):
    segs = []
    for i in range(len(zc_all) - 1):
        s_idx, e_idx = zc_all[i], zc_all[i + 1]
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
        s["dir"] = "P" if s["is_pos"] else "N"
    return segs


# ============================================================
# 合併同向波（與原版相同）
# ============================================================

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
                s0, s1 = segs[i], segs[i + 1]
                gap = s1["st"] - s0["ed"]
                if s0["dir"] == s1["dir"] and gap <= max_bridge_sec:
                    merged = {
                        "i": s0.get("i", 0),
                        "s_idx": s0["s_idx"],
                        "e_idx": s1["e_idx"],
                        "st": s0["st"],
                        "ed": s1["ed"],
                        "duration": s1["ed"] - s0["st"],
                        "dir": s0["dir"],
                        "is_pos": s0.get("is_pos", s0["dir"] == "P"),
                        "energy": s0.get("energy", 0) + s1.get("energy", 0),
                    }
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


# ============================================================
# DEMO 能量計算
# 【改動】改用全段 detrend，不再用 ±2s window
#
# 舊版：只取 DEMO±2s 的 8 秒資料 → 獨立 lowpass → 獨立 7s MA → detrend
# 新版：全段 lowpass → 全段 7s MA → 全段 detrend → 只取 DEMO 時間範圍算能量
# ============================================================

def compute_demo_features(t_all, r_all, mask_demo, fs, cutoff, dir_eff):
    """
    計算 DEMO 段的能量密度，回傳 (E_demo, energy_threshold)

    【全段 detrend 版】
    1. 對全段信號做 lowpass + 7s 移動平均 baseline
    2. 全段 detrend = filtered - baseline
    3. 只取 DEMO 時間範圍內的 detrend 值算能量密度
    """
    if mask_demo is None or mask_demo.sum() < 3:
        return 0.0, 0.0

    idx_demo = np.flatnonzero(mask_demo)
    if idx_demo.size < 2:
        return 0.0, 0.0

    t_demo_start = float(t_all[idx_demo[0]])
    t_demo_end   = float(t_all[idx_demo[-1]])

    # --- 全段 lowpass + 7s MA baseline + detrend ---
    r_all_filt = lowpass_filter(r_all, fs=fs, cutoff=cutoff, order=ORDER)
    baseline_all = moving_average(r_all_filt, int(7.0 * fs))
    r_all_det = r_all_filt - baseline_all

    # --- 只取 DEMO 範圍算能量 ---
    E_demo = energy_density_interval_dir(
        r_all_det, t_all, fs,
        t_demo_start, t_demo_end, dir_eff
    )

    energy_thr = R_DEMO * E_demo
    return float(E_demo), float(energy_thr)


# ============================================================
# 能量密度計算（與原版相同）
# ============================================================

def energy_density_interval_dir(x, t, fs, t0, t1, dir_eff):
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


# ============================================================
# 分析高峰 / 低谷（與原版相同）
# ============================================================

def analyze_high_peaks(r_detrend, t, fs, seg, energy_threshold, dir_eff="P"):
    spans = []
    s_idx, e_idx = seg["s_idx"], seg["e_idx"]
    seg_data = r_detrend[s_idx:e_idx]
    seg_t = t[s_idx:e_idx]

    if seg_data.size == 0:
        return spans

    duration = seg_t[-1] - seg_t[0]
    if duration < MIN_ACTION_DURATION or duration > MAX_ACTION_DURATION:
        return spans

    seg_energy = energy_density_interval_dir(seg_data, seg_t, fs, seg_t[0], seg_t[-1], dir_eff)

    if seg_energy >= energy_threshold:
        peak_val = np.max(seg_data)
        peak_idx = np.argmax(seg_data)
        peak_time = t[s_idx + peak_idx]
        spans.append({
            "type": "P", "dir": "P",
            "i": seg["i"], "is_pos": True,
            "s_idx": s_idx, "e_idx": e_idx,
            "st": float(seg_t[0]), "ed": float(seg_t[-1]),
            "duration": float(duration),
            "energy": float(seg_energy),
            "energy_thr": float(energy_threshold),
            "peak_time": float(peak_time),
            "peak_val": float(peak_val),
        })
    return spans


def analyze_low_troughs(r_detrend, t, fs, seg, energy_threshold, dir_eff="N"):
    spans = []
    s_idx, e_idx = seg["s_idx"], seg["e_idx"]
    seg_data = r_detrend[s_idx:e_idx]
    seg_t = t[s_idx:e_idx]

    if seg_data.size == 0:
        return spans

    duration = seg_t[-1] - seg_t[0]
    if duration < MIN_ACTION_DURATION or duration > MAX_ACTION_DURATION:
        return spans

    seg_energy = energy_density_interval_dir(seg_data, seg_t, fs, seg_t[0], seg_t[-1], dir_eff)

    if seg_energy >= energy_threshold:
        trough_val = np.min(seg_data)
        trough_idx = np.argmin(seg_data)
        trough_time = t[s_idx + trough_idx]
        spans.append({
            "type": "N", "dir": "N",
            "i": seg["i"], "is_pos": False,
            "s_idx": s_idx, "e_idx": e_idx,
            "st": float(seg_t[0]), "ed": float(seg_t[-1]),
            "duration": float(duration),
            "energy": float(seg_energy),
            "energy_thr": float(energy_threshold),
            "trough_time": float(trough_time),
            "trough_val": float(trough_val),
        })
    return spans


# ============================================================
# 畫圖（與原版相同）
# ============================================================

def plot_analysis(t, r_raw, r_filt, baseline, r_detrend,
                  zc_all, zc_up, zc_down,
                  segments, action_spans,
                  dir_eff, demo_energy, energy_threshold,
                  t_all, r_all, mask_demo,
                  fs, cutoff, title="CHEEK Analysis"):
    if not HAS_PLOT:
        return

    fig, axes = plt.subplots(4, 1, figsize=(14, 12))
    fig.suptitle(f"{title}\nDir={dir_eff}, FS={fs:.1f}Hz, Cutoff={cutoff:.2f}Hz", fontsize=12)

    ax1 = axes[0]
    ax1.plot(t_all, r_all, 'b-', alpha=0.6, label='Raw Dihedral (Full)', linewidth=0.8)
    if mask_demo is not None and np.any(mask_demo):
        idx_demo = np.flatnonzero(mask_demo)
        if idx_demo.size > 0:
            demo_start = t_all[idx_demo[0]]
            demo_end = t_all[idx_demo[-1]]
            ax1.axvspan(demo_start, demo_end, alpha=0.3, color='yellow', label='DEMO')
            ax1.axvspan(demo_start - DEMO_SIDE_SEC, demo_start, alpha=0.2, color='orange', label='Left Calib')
            ax1.axvspan(demo_end, demo_end + DEMO_SIDE_SEC, alpha=0.2, color='orange', label='Right Calib')
    if len(t) > 0:
        ax1.axvspan(t[0], t[-1], alpha=0.1, color='green', label='MAINTAINING')
    ax1.set_ylabel('Dihedral (deg)')
    ax1.set_title('Full Signal (with DEMO + Calibration Zones)')
    ax1.legend(loc='upper right', fontsize=8)
    ax1.grid(True, alpha=0.3)

    ax2 = axes[1]
    ax2.plot(t, r_filt, 'g-', label='Filtered', linewidth=1)
    ax2.plot(t, baseline, 'r--', label='Baseline', linewidth=1.5)
    ax2.set_ylabel('Dihedral (deg)')
    ax2.set_title('Filtered Signal + Baseline (MAINTAINING only)')
    ax2.legend(loc='upper right')
    ax2.grid(True, alpha=0.3)

    ax3 = axes[2]
    ax3.plot(t, r_detrend, 'b-', label='Detrended', linewidth=1)
    ax3.axhline(y=0, color='k', linestyle='-', linewidth=0.5)
    if len(zc_up) > 0:
        ax3.scatter(t[zc_up], r_detrend[zc_up], c='green', s=30, marker='^', label='ZC Up', zorder=5)
    if len(zc_down) > 0:
        ax3.scatter(t[zc_down], r_detrend[zc_down], c='red', s=30, marker='v', label='ZC Down', zorder=5)
    for i, span in enumerate(action_spans):
        color = 'lime' if span["dir"] == "P" else 'cyan'
        ax3.axvspan(span["st"], span["ed"], alpha=0.3, color=color)
        if "peak_time" in span:
            ax3.scatter([span["peak_time"]], [span["peak_val"]], c='red', s=100, marker='*', zorder=10)
            ax3.annotate(f'#{i+1}', (span["peak_time"], span["peak_val"]),
                        textcoords="offset points", xytext=(0, 10), ha='center', fontsize=8)
        if "trough_time" in span:
            ax3.scatter([span["trough_time"]], [span["trough_val"]], c='blue', s=100, marker='*', zorder=10)
            ax3.annotate(f'#{i+1}', (span["trough_time"], span["trough_val"]),
                        textcoords="offset points", xytext=(0, -15), ha='center', fontsize=8)
    ax3.set_ylabel('Detrended (deg)')
    ax3.set_title(f'Detrended Signal + Actions (Count={len(action_spans)})')
    ax3.legend(loc='upper right')
    ax3.grid(True, alpha=0.3)

    ax4 = axes[3]
    seg_centers, seg_energies, seg_colors = [], [], []
    for seg in segments:
        center = (seg["st"] + seg["ed"]) / 2
        energy = seg.get("energy", 0)
        seg_centers.append(center)
        seg_energies.append(energy)
        is_action = any((span["st"] == seg["st"] and span["ed"] == seg["ed"]) for span in action_spans)
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


# ============================================================
# 主分析函數
# ============================================================

def analyze_csv(file_path: str, plot: bool = True) -> dict:
    try:
        df = pd.read_csv(file_path)
        if len(df) < 10:
            return {"status": "ERROR", "error": "資料太少 (< 10 rows)"}

        t_all = pd.to_numeric(df["time_seconds"], errors="coerce").to_numpy()

        r_all = compute_dihedral_series(df)

        m_all = np.isfinite(t_all) & np.isfinite(r_all)
        t_all, r_all = t_all[m_all], r_all[m_all]

        mask_demo = None
        if "state" in df.columns:
            s = df["state"].astype(str)
            mask_demo_all = s.str.contains("DEMO", case=False, na=False).to_numpy()
            mask_demo = mask_demo_all[m_all]

        dir_eff = infer_dir_from_demo_by_series(t_all, r_all, mask_demo) or "N"
        print(f"[DIR] using = {dir_eff}")

        if "state" in df.columns:
            df_main = df[df["state"].astype(str).str.contains("MAINTAINING", case=False, na=False)].copy()
        else:
            df_main = df.copy()

        t = pd.to_numeric(df_main["time_seconds"], errors="coerce").to_numpy()

        r = compute_dihedral_series(df_main)

        m = np.isfinite(t) & np.isfinite(r)
        t, r = t[m], r[m]

        if t.size < 2:
            return {
                "status": "OK", "action_count": 0,
                "total_action_time": 0.0, "breakpoints": [], "segments": [],
                "curve": [], "debug": {"note": "insufficient MAINTAINING data"}
            }

        fs = calculate_fs_from_csv(file_path)
        cutoff_auto = auto_cutoff_from_signal(t, r, fs)

        r_filt = lowpass_filter(r, fs=fs, cutoff=cutoff_auto, order=ORDER)
        baseline = moving_average(r_filt, int(7.0 * fs))
        r_detrend = r_filt - baseline

        std = float(np.std(r_detrend)) if len(r_detrend) else 0.0
        deadband = 0.000 * std if std > 0 else 0.0
        min_interval = int(0.5 * fs)
        zc_all, zc_up, zc_down = zero_crossings(r_detrend, min_interval, deadband)

        segments = build_segments_from_zc(zc_all, r_detrend, t)

        demo_energy, energy_threshold = compute_demo_features(
            t_all, r_all, mask_demo, fs, cutoff_auto, dir_eff
        )
        print(f"[ENERGY] DEMO={demo_energy:.2e}, Threshold={energy_threshold:.2e}")

        action_spans = []
        pos_waves = neg_waves = 0

        for seg in segments:
            s_idx, e_idx = seg["s_idx"], seg["e_idx"]
            seg_data = r_detrend[s_idx:e_idx]
            seg_t = t[s_idx:e_idx]
            if seg_data.size == 0:
                continue
            seg_energy = energy_density_interval_dir(seg_data, seg_t, fs, seg_t[0], seg_t[-1], seg["dir"])
            seg["energy"] = seg_energy
            if seg["is_pos"]:
                pos_waves += 1
            else:
                neg_waves += 1
            if dir_eff == "P" and seg["is_pos"]:
                spans = analyze_high_peaks(r_detrend, t, fs, seg, energy_threshold, "P")
                action_spans.extend(spans)
            if dir_eff == "N" and not seg["is_pos"]:
                spans = analyze_low_troughs(r_detrend, t, fs, seg, energy_threshold, "N")
                action_spans.extend(spans)

        action_spans = merge_segments_by_time(action_spans, BRIDGE_MAX_SEC)

        action_count = len(action_spans)
        total_action_time = sum(span["duration"] for span in action_spans)

        if plot and HAS_PLOT:
            filename = file_path.split("\\")[-1].split("/")[-1]
            plot_analysis(
                t, r, r_filt, baseline, r_detrend,
                zc_all, zc_up, zc_down,
                segments, action_spans,
                dir_eff, demo_energy, energy_threshold,
                t_all, r_all, mask_demo,
                fs, cutoff_auto,
                title=f"CHEEK (Dihedral): {filename}"
            )

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
                "feature": "dihedral_angle",
                "demo_energy_method": "full_signal_detrend",  # 【新增】標記用全段
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


# ============================================================
# 測試用
# ============================================================
if __name__ == "__main__":
    csv_files = [

        # 在這裡放你的 CSV 路徑
        #             r"C:\Users\plus1\Downloads\年後個案分析\rhuser03\rhuser03\PUFF_CHEEK\rhuser03_FaceTraining_PUFF_CHEEK_20260131_064314.csv",
        #     r"C:\Users\plus1\Downloads\年後個案分析\rhuser03\rhuser03\PUFF_CHEEK\rhuser03_FaceTraining_PUFF_CHEEK_20260131_215626.csv",
        #     r"C:\Users\plus1\Downloads\年後個案分析\rhuser03\rhuser03\PUFF_CHEEK\rhuser03_FaceTraining_PUFF_CHEEK_20260204_114706.csv",
        #     r"C:\Users\plus1\Downloads\年後個案分析\rhuser03\rhuser03\PUFF_CHEEK\rhuser03_FaceTraining_PUFF_CHEEK_20260204_144139.csv",
        # r"C:\Users\plus1\Downloads\年後個案分析\rhuser03\rhuser03\PUFF_CHEEK\rhuser03_FaceTraining_PUFF_CHEEK_20260204_181130.csv"
# r"C:\Users\plus1\Downloads\lpcsuser01_FaceTraining_PUFF_CHEEK_20260301_154652.csv"
# r"C:\Users\plus1\Downloads\lpcsuser01_FaceTraining_PUFF_CHEEK_20260301_155231.csv"
# r"C:\Users\plus1\Downloads\lpcsuser01_FaceTraining_PUFF_CHEEK_20260301_155547.csv"
]


    print("=" * 80)
    all_results = {}

    for i, csv_path in enumerate(csv_files, 1):
        filename = csv_path.split("\\")[-1]
        print(f"\n[{i}/{len(csv_files)}] 📁 {filename}")
        print("-" * 80)

        result = analyze_csv(csv_path, plot=True)
        all_results[filename] = result

        print(f"狀態: {result.get('status')}")
        print(f"動作次數: {result.get('action_count')}")
        print(f"總動作時間: {result.get('total_action_time')} 秒")

        if result.get('status') == 'ERROR':
            print(f"❌ 錯誤: {result.get('error')}")

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