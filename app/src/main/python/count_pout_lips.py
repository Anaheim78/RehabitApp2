import io, csv
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt

# ===== 參數 =====
FS = 30.0
CUTOFF = 0.8
ORDER = 4

# ===== 低通濾波器 =====
def lowpass_filter(x, fs=FS, cutoff=CUTOFF, order=ORDER):
    b, a = butter(order, cutoff / (fs / 2), btype='low')
    y = filtfilt(b, a, x)
    return y

# ===== 移動平均（基線估計）=====
def moving_average(x, win_samples):
    if win_samples < 1:
        win_samples = 1
    kernel = np.ones(win_samples) / win_samples
    pad_width = win_samples // 2
    x_padded = np.pad(x, pad_width, mode='edge')
    baseline_full = np.convolve(x_padded, kernel, mode='same')
    baseline = baseline_full[pad_width:-pad_width]
    return baseline

# ===== 零交叉檢測 =====
def zero_crossings(x, t, deadband=0.0, min_interval=10):
    crossings_all, crossings_up, crossings_down = [], [], []
    last_idx = -min_interval
    for i in range(1, len(x)):
        if np.isnan(x[i-1]) or np.isnan(x[i]):
            continue
        # 負 -> 正
        if x[i-1] < 0 and x[i] >= 0 and abs(x[i]) > deadband:
            if i - last_idx >= min_interval:
                crossings_all.append(i)
                crossings_up.append(i)
                last_idx = i
        # 正 -> 負
        elif x[i-1] > 0 and x[i] <= 0 and abs(x[i]) > deadband:
            if i - last_idx >= min_interval:
                crossings_all.append(i)
                crossings_down.append(i)
                last_idx = i
    return crossings_all, crossings_up, crossings_down

# ===== 動作篩選 =====
def filter_actions(segments, min_duration=0.5, min_gap=0.5):
    actions = []
    last_end = -1e9
    for seg in segments:
        if seg["duration"] < min_duration:
            continue
        if seg["start_time"] - last_end < min_gap:
            continue
        actions.append(seg)
        last_end = seg["end_time"]
    return actions

# ===== 主流程 =====
def analyze_csv(file_path: str) -> dict:
    """
    讀取 CSV，只保留 state=MAINTAINING，
    然後低通 + baseline 扣除 + 零交叉 + 動作篩選
    """
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            df = pd.DataFrame(csv.DictReader(f))
        lowmap = {str(c).strip().lower(): c for c in df.columns if c is not None}

        # 檢查必要欄位
        if "time_seconds" not in lowmap or "state" not in lowmap or "outer_mouth_z_avg" not in lowmap:
            return {"status": "ERROR", "error": "缺少必要欄位"}

        # 只保留 MAINTAINING
        df = df[df[lowmap["state"]] == "MAINTAINING"]

        # 數據轉 numpy
        t_raw = pd.to_numeric(df[lowmap["time_seconds"]], errors="coerce").to_numpy()
        r_raw = pd.to_numeric(df[lowmap["outer_mouth_z_avg"]], errors="coerce").to_numpy()
        m = np.isfinite(t_raw) & np.isfinite(r_raw)
        t, r = t_raw[m], r_raw[m]

        if len(t) < 2:
            return {"status": "OK", "action_count": 0, "total_action_time": 0.0,
                    "breakpoints": [], "segments": [], "debug": {"note": "insufficient data"}}

        # 低通
        r_filt = lowpass_filter(r, fs=FS, cutoff=CUTOFF, order=ORDER)

        # 基線扣除
        win = int(4.0 * FS)
        baseline = moving_average(r_filt, win)
        r_detrend = r_filt - baseline

        # 零交叉，min_interval=>間隔點需要至少大於多少
        deadband = 0.001 * float(np.std(r_detrend)) if np.std(r_detrend) > 0 else 0.0
        min_interval = int(0.2 * FS)
        zc_all, zc_up, zc_down = zero_crossings(r_detrend, t, deadband=deadband, min_interval=min_interval)

        # 建 segments
#         segments = []
#         if len(zc_all) >= 2:
#             for i, (s, e) in enumerate(zip(zc_all[:-1], zc_all[1:])):
#                 st, ed = float(t[s]), float(t[e])
#                 dur = round(ed - st, 3)
#                 segments.append({"index": i, "start_time": st, "end_time": ed, "duration": dur})
# 建 segments，只保留負半週
        segments = []
        if len(zc_all) >= 2:
            for i, (s, e) in enumerate(zip(zc_all[:-1], zc_all[1:])):
                st, ed = float(t[s]), float(t[e])
                dur = round(ed - st, 3)

                # 判斷平均值是否小於 0 → 負半週（嘴巴往前）
                if np.mean(r_detrend[s:e]) < 0:
                    segments.append({
                        "index": i,
                        "start_time": round(st, 3),
                        "end_time": round(ed, 3),
                        "duration": dur
                    })



        # 篩選動作
        actions = filter_actions(segments, min_duration=0.5, min_gap=0.5)

        breakpoints = [seg["end_time"] for seg in segments]
        total_action_time = round(sum(seg["duration"] for seg in actions), 3)

        return {
            "status": "OK",
            "action_count": len(actions),
            "total_action_time": total_action_time,
            "breakpoints": breakpoints,
            "segments": segments,
            "debug": {
                "fs_hz": FS, "cutoff": CUTOFF, "order": ORDER,
                "zc_all": len(zc_all), "zc_up": len(zc_up), "zc_down": len(zc_down),
                "deadband": round(deadband, 6), "min_interval": min_interval
            }
        }
    except Exception as e:
        return {"status": "ERROR", "error": str(e)}
