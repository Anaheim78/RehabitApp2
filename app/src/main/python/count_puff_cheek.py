import math
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt

# === cheek landmark sets ===
LEFT_CHEEK_IDXS  = [117,118,101,36,203,212,214,192,147,123,98,97,164,0,37,39,40,186]
RIGHT_CHEEK_IDXS = [164,0,267,269,270,410,423,327,326,432,434,416,376,352,346,347,330,266]

# === params ===
FS_DEFAULT         = 10.0
CUTOFF             = 0.3
ORDER              = 4
THRESHOLD          = 2e-6

# 〈同向波合併〉參數
MERGE_ENABLE          = True
BRIDGE_MAX_SEC        = 0.8
BRIDGE_MAX_RANGE_RATE = 0.6

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
def lowpass_filter(x, fs=FS_DEFAULT, cutoff=CUTOFF, order=ORDER):
    x = np.asarray(x, dtype=float)
    if x.size < 8:
        return x
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    return filtfilt(b, a, x)

def moving_average(x, win_samples):
    win = max(1, int(win_samples))
    ker = np.ones(win) / win
    pad = win // 2
    xpad = np.pad(x, pad, mode='edge')
    base = np.convolve(xpad, ker, mode='same')
    return base[pad:-pad]

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
        # 負→正
        if xi_1 <= 0 and xi > 0 and abs(xi) > deadband:
            if i - last >= min_interval:
                z_all.append(i)
                z_up.append(i)
                last = i
        # 正→負
        elif xi_1 >= 0 and xi < 0 and abs(xi) > deadband:
            if i - last >= min_interval:
                z_all.append(i)
                z_down.append(i)
                last = i
    return z_all, z_up, z_down

# ===== 方向判斷 (從 DEMO 段) =====
def infer_dir_from_demo_by_series(t_all, r_all, mask_demo, ax=None, side_sec=2.0):
    """
    方向判斷（新版穩定版）：
    - 取 DEMO 段前 side_sec 秒 與 後 side_sec 秒
    - 各自取平均曲率，連成一條線
    - 比較 DEMO 段曲線相對這條線的面積：
        若上方面積 > 下方面積 → 'P'（往外鼓）
        否則 → 'N'（往內吸）
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

    # 左右平均值
    r_left_avg  = np.mean(r_all[idx_left])
    r_right_avg = np.mean(r_all[idx_right])

    # DEMO 段內資料
    t_demo = t_all[idx_demo]
    r_demo = r_all[idx_demo]

    # 基準線（外兩秒平均連線）
    base = r_left_avg + (r_right_avg - r_left_avg) * (t_demo - t_demo[0]) / (t_demo[-1] - t_demo[0])
    diff = r_demo - base

    pos_area = np.trapz(np.clip(diff,  0, None), t_demo)
    neg_area = np.trapz(np.clip(-diff, 0, None), t_demo)
    d = "P" if pos_area > neg_area else "N"

    print(f"[DIR-new] Lavg={r_left_avg:.3e}, Ravg={r_right_avg:.3e}, pos={pos_area:.2e}, neg={neg_area:.2e}, dir={d}")

    if ax is not None:
        ax.plot([t_demo[0], t_demo[-1]], [r_left_avg, r_right_avg],
                color='purple', linestyle='--', linewidth=1.5, alpha=0.7, label='avg-line')
        ax.fill_between(t_demo, r_demo, base, where=(diff>=0), alpha=0.25, color='limegreen')
        ax.fill_between(t_demo, r_demo, base, where=(diff<0),  alpha=0.25, color='salmon')
        ax.legend()
        ax.text((t_demo[0]+t_demo[-1])/2, (r_left_avg+r_right_avg)/2, d,
                color='black', fontsize=12, fontweight='bold', va='bottom')

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
    return segs

# ===== 合併同向波 =====
def merge_same_direction_waves(segs, r_detrend, t, dir_eff, fs,
                               bridge_max_sec=BRIDGE_MAX_SEC,
                               bridge_max_range=BRIDGE_MAX_RANGE_RATE * THRESHOLD):
    if not MERGE_ENABLE or len(segs) < 3:
        return segs

    target_is_pos = (dir_eff == "P")
    changed = True
    while changed:
        changed = False
        out = []
        i = 0
        while i < len(segs):
            if i + 2 < len(segs):
                s0, s1, s2 = segs[i], segs[i+1], segs[i+2]
                # 左右同向且都是目標方向,中間為反向且很小
                if (s0["is_pos"] == s2["is_pos"] == target_is_pos) and (s1["is_pos"] != target_is_pos):
                    dur_bridge = t[s1["e_idx"]] - t[s1["s_idx"]]
                    bridge_data = r_detrend[s1["s_idx"]:s1["e_idx"]]
                    amp_range = float(np.max(bridge_data) - np.min(bridge_data)) if bridge_data.size else 0.0

                    if (dur_bridge <= bridge_max_sec) and (amp_range <= bridge_max_range):
                        # 合併 s0~s2
                        ms = {
                            "i": s0["i"],
                            "s_idx": s0["s_idx"],
                            "e_idx": s2["e_idx"],
                            "st": s0["st"],
                            "ed": s2["ed"],
                        }
                        data = r_detrend[ms["s_idx"]:ms["e_idx"]]
                        ms["mean"] = float(np.mean(data)) if data.size else 0.0
                        ms["is_pos"] = (ms["mean"] >= 0)
                        out.append(ms)
                        i += 3
                        changed = True
                        continue
            out.append(segs[i])
            i += 1
        segs = out
    return segs

# ===== 主分析函數 =====
def analyze_csv(file_path: str) -> dict:
    """
    輸入: CSV 檔案路徑
    輸出: dict 格式 (標準格式，與 count_puff_cheek.py 一致)
    {
        "status": "OK" | "ERROR",
        "action_count": int,
        "total_action_time": float,
        "breakpoints": [float, ...],
        "segments": [{"index": int, "start_time": float, "end_time": float, "duration": float}, ...],
        "curve": [{"t": float, "v": str}, ...],
        "debug": {dict}
    }

    內部邏輯:
    - 自動判斷方向 (P/N) 從 DEMO 段
    - 同向波合併
    - Delta 閾值篩選
    - 只統計符合方向且 Delta >= THRESHOLD 的動作
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

        # === 濾波/基線/去趨勢 ===
        fs = calculate_fs_from_csv(file_path)
        r_filt = lowpass_filter(r, fs=fs, cutoff=CUTOFF, order=ORDER)
        baseline = moving_average(r_filt, int(4.0 * fs))
        r_detrend = r_filt - baseline

        # === 零交叉 ===
        std = float(np.std(r_detrend)) if len(r_detrend) else 0.0
        deadband = 0.005 * std if std > 0 else 0.0
        min_interval = int(0.2 * fs)
        zc_all, zc_up, zc_down = zero_crossings(r_detrend, min_interval, deadband)

        # === 建段 & 合併 ===
        segments = build_segments_from_zc(zc_all, r_detrend, t)
        segments = merge_same_direction_waves(segments, r_detrend, t, dir_eff, fs)

        # === 計算 Delta 並統計 ===
        pos_waves = neg_waves = 0
        action_count = 0
        deltas_info = []
        total_action_time = 0.0

        for i, seg in enumerate(segments):
            st, ed = seg["st"], seg["ed"]
            s_idx, e_idx = seg["s_idx"], seg["e_idx"]
            seg_data = r_detrend[s_idx:e_idx]

            if seg_data.size == 0:
                continue

            if seg["is_pos"]:
                pos_waves += 1
            else:
                neg_waves += 1

            # === 正半波 (P 方向) ===
            if dir_eff == "P" and seg["is_pos"]:
                peak_val = np.max(seg_data)
                peak_idx = np.argmax(seg_data)
                peak_time = t[s_idx + peak_idx]

                prev_min = np.min(r_detrend[segments[i-1]["s_idx"]:segments[i-1]["e_idx"]]) if i > 0 else peak_val
                next_min = np.min(r_detrend[segments[i+1]["s_idx"]:segments[i+1]["e_idx"]]) if i < len(segments)-1 else peak_val
                diff_max = max(peak_val - prev_min, peak_val - next_min)

                if diff_max >= THRESHOLD:
                    action_count += 1
                    total_action_time += (ed - st)
                    deltas_info.append({
                        'type': 'P',
                        'delta': round(diff_max, 10),
                        'peak_time': round(peak_time, 3),
                        'peak_val': round(peak_val, 10),
                        'segment_start': round(st, 3),
                        'segment_end': round(ed, 3),
                        'duration': round(ed - st, 3)
                    })

            # === 負半波 (N 方向) ===
            if dir_eff == "N" and not seg["is_pos"]:
                trough_val = np.min(seg_data)
                trough_idx = np.argmin(seg_data)
                trough_time = t[s_idx + trough_idx]

                prev_max = np.max(r_detrend[segments[i-1]["s_idx"]:segments[i-1]["e_idx"]]) if i > 0 else trough_val
                next_max = np.max(r_detrend[segments[i+1]["s_idx"]:segments[i+1]["e_idx"]]) if i < len(segments)-1 else trough_val
                diff_max = max(prev_max - trough_val, next_max - trough_val)

                if diff_max >= THRESHOLD:
                    action_count += 1
                    total_action_time += (ed - st)
                    deltas_info.append({
                        'type': 'N',
                        'delta': round(diff_max, 10),
                        'trough_time': round(trough_time, 3),
                        'trough_val': round(trough_val, 10),
                        'segment_start': round(st, 3),
                        'segment_end': round(ed, 3),
                        'duration': round(ed - st, 3)
                    })

        # === 輸出曲線資料 ===
        curve = [{"t": round(float(tt), 3), "v": f"{vv:.10f}"} for tt, vv in zip(t, r_detrend)]

        # === 產生 breakpoints (所有段的結束時間) ===
        breakpoints = [round(seg["ed"], 3) for seg in segments]

        # === 組裝輸出 (標準格式) ===
        return {
            "status": "OK",
            "action_count": action_count,
            "total_action_time": round(total_action_time, 3),
            "breakpoints": breakpoints,
            "segments": [
                {
                    "index": i,
                    "start_time": round(seg["st"], 3),
                    "end_time": round(seg["ed"], 3),
                    "duration": round(seg["ed"] - seg["st"], 3)
                }
                for i, seg in enumerate(segments)
            ],
            "curve": curve,
            "debug": {
                "fs_hz": fs,
                "cutoff": CUTOFF,
                "order": ORDER,
                "threshold": THRESHOLD,
                "total_segments": len(segments),
                "zc_all": len(zc_all),
                "zc_up": len(zc_up),
                "zc_down": len(zc_down),
                "deadband": round(deadband, 6),
                "min_interval": min_interval
            }
        }

    except Exception as e:
        return {"status": "ERROR", "error": str(e)}


# ===== 測試用 =====
if __name__ == "__main__":
    # 測試範例
    test_file = r"C:\Users\plus1\Downloads\FaceTraining_PUFF_CHEEK_20251030_160710_4變5.csv"
    result = analyze_csv(test_file)

    print("\n" + "="*60)
    print("📊 Analysis Result:")
    print("="*60)
    print(f"Status: {result.get('status')}")
    print(f"Action Count: {result.get('action_count')}")
    print(f"Total Action Time: {result.get('total_action_time')}s")
    print(f"Total Segments: {len(result.get('segments', []))}")
    print(f"Breakpoints: {len(result.get('breakpoints', []))}")
    print("\n" + "="*60)