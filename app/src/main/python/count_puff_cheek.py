
import io, csv, math
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt

# 臉頰點位（左右各 18 個）
LEFT_CHEEK_IDXS  = [117,118,101,36,203,212,214,192,147,123,98,97,164,0,37,39,40,186]
RIGHT_CHEEK_IDXS = [164,0,267,269,270,410,423,327,326,432,434,416,376,352,346,347,330,266]

# 濾波參數（與其他模組一致）
FS = 10.0
CUTOFF = 0.3
ORDER = 4

# ===== 濾波 & 前處理 =====
def lowpass_filter(x, fs=FS, cutoff=CUTOFF, order=ORDER):
    x = np.asarray(x, dtype=float)
    # filtfilt 需要最小長度；不足時直接回原訊號
    min_len = max(order * 3, 8)
    if x.size < min_len:
        return x
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    return filtfilt(b, a, x)

def moving_average(x, win_samples):
    win = max(1, int(win_samples))
    ker = np.ones(win, dtype=float) / win
    pad = win // 2
    xpad = np.pad(x, pad_width=pad, mode="edge")
    base_full = np.convolve(xpad, ker, mode="same")
    return base_full[pad:-pad] if pad > 0 else base_full

def zero_crossings(x, t, deadband=0.0, min_interval=10):
    z_all, z_up, z_dn = [], [], []
    last = -min_interval
    for i in range(1, len(x)):
        xi_1, xi = x[i-1], x[i]
        if not np.isfinite(xi_1) or not np.isfinite(xi):
            continue
        # 負→正
        if xi_1 < 0 and xi >= 0 and abs(xi) > deadband:
            if i - last >= min_interval:
                z_all.append(i); z_up.append(i); last = i
        # 正→負
        elif xi_1 > 0 and xi <= 0 and abs(xi) > deadband:
            if i - last >= min_interval:
                z_all.append(i); z_dn.append(i); last = i
    return z_all, z_up, z_dn

# ===== 曲率（依你本地算法）=====
def fit_quadratic_surface_xyz(x, y, z):
    n = x.shape[0]
    if n < 6:
        return (0., 0., 0., 0., 0., 0.)
    A = np.column_stack([x*x, x*y, y*y, x, y, np.ones_like(x)])
    try:
        coef, *_ = np.linalg.lstsq(A, z, rcond=None)
        return tuple(coef.tolist())
    except np.linalg.LinAlgError:
        return (0., 0., 0., 0., 0., 0.)

def curvature_proxy_from_quad(a, b, c):
    # 只取二次項當作曲率 proxy
    return math.sqrt((2*a)**2 + (2*c)**2 + 2*(b**2))

def cheek_patch_curvature(points3d):
    """
    points3d: shape (N, 3), columns = [x, y, z]
    """
    points3d = np.asarray(points3d, dtype=np.float32)
    if points3d.shape[0] < 6:
        return 0.0
    x = points3d[:, 0]
    y = points3d[:, 1]
    z = points3d[:, 2]
    a, b, c, d, e, f = fit_quadratic_surface_xyz(x, y, z)
    return curvature_proxy_from_quad(a, b, c)

# ===== 行別工具 =====
def _row_points3d(row, idxs):
    pts = []
    for k in idxs:
        px = float(row[f"point{k}_x"])
        py = float(row[f"point{k}_y"])
        pz = float(row[f"point{k}_z"])
        pts.append([px, py, pz])
    return np.asarray(pts, dtype=float)  # (N, 3)

# ===== 主流程（只做鼓臉，計「> 0」的段）=====
def analyze_csv(file_path: str) -> dict:
    try:

        # 讀檔
        df = pd.read_csv(file_path)
        need_cols = [f"point{k}_{ax}" for k in LEFT_CHEEK_IDXS + RIGHT_CHEEK_IDXS for ax in ("x", "y", "z")]
        need_cols = ["time_seconds", "state"] + need_cols + ["img_w", "img_h"]
        missing = [c for c in need_cols if c not in df.columns]
        if missing:
            return {"status": "ERROR", "error": f"missing columns ({len(missing)})", "missing_preview": missing[:10]}

        # **只保留 state == MAINTAINING**
        df = df[df["state"] == "MAINTAINING"]

        if len(df) < 2:
            return {
                "status": "OK", "action_count": 0, "total_action_time": 0.0,
                "breakpoints": [], "segments": [],
                "debug": {"fs_hz": FS, "cutoff": CUTOFF, "order": ORDER, "note": "insufficient rows"}
            }
        # 每列重建 P_L/P_R → 曲率 → 時序
        curv_L, curv_R, t_list = [], [], []
        for _, row in df.iterrows():
            P_L = _row_points3d(row, LEFT_CHEEK_IDXS)
            P_R = _row_points3d(row, RIGHT_CHEEK_IDXS)
            cL = cheek_patch_curvature(P_L)
            cR = cheek_patch_curvature(P_R)
            curv_L.append(cL)
            curv_R.append(cR)
            t_list.append(float(row["time_seconds"]))

        t = np.asarray(t_list, dtype=float)
        s = np.asarray(curv_L, dtype=float) + np.asarray(curv_R, dtype=float)  # L+R

        # 前處理：低通 → 基線 → 扣除
        s_f = lowpass_filter(s, fs=FS, cutoff=CUTOFF, order=ORDER)
        baseline = moving_average(s_f, int(4.0 * FS))
        # 對齊長度保護（極短序列時 moving_average 會回原長或近原長）
        L = min(len(s_f), len(baseline))
        s_d = s_f[:L] - baseline[:L]
        t = t[:L]

        # 零交叉（取 >0 的段）
        std = float(np.std(s_d)) if len(s_d) else 0.0
        deadband = 0.005 * std if std > 0 else 0.0
        min_interval = int(0.5 * FS)
        zc_all, zc_up, zc_down = zero_crossings(s_d, t, deadband=deadband, min_interval=min_interval)

        segments = []
        positive_segments = []
        if len(zc_all) >= 2:
            for i, (s_idx, e_idx) in enumerate(zip(zc_all[:-1], zc_all[1:])):
                st, ed = float(t[s_idx]), float(t[e_idx])
                dur = round(ed - st, 3)
                seg = {"index": i, "start_time": round(st, 3), "end_time": round(ed, 3), "duration": dur}
                segments.append(seg)
                # 只計「>0」區間
                if s_d[s_idx] >= 0:
                    positive_segments.append(seg)

        action_count = len(positive_segments)
        total_action_time = round(sum(seg["duration"] for seg in positive_segments), 3)
        breakpoints = [seg["end_time"] for seg in segments]

        # === 新增曲線輸出（time,value list） ===
        # curve = [{"t": round(float(tt), 3), "v": round(float(vv), 6)}
        #          for tt, vv in zip(t, s_d)]
        # curve = [{"t": float(tt), "v": float(vv)} for tt, vv in zip(t, s_d)]
        curve = [{"t": round(float(tt), 3), "v": f"{vv:.10f}"}
         for tt, vv in zip(t, s_d)]


        return {
            "status": "OK",
            "action_count": action_count,
            "total_action_time": total_action_time,
            "breakpoints": breakpoints,
            "segments": segments,
            "curve": curve,
            "debug": {
                "fs_hz": FS,
                "cutoff": CUTOFF,
                "order": ORDER,
                "zc_all": len(zc_all),
                "zc_up": len(zc_up),
                "zc_down": len(zc_down),
                "deadband": round(deadband, 6),
                "min_interval": min_interval
            }
        }

    except Exception as e:
        return {"status": "ERROR", "error": str(e)}
