import io, csv
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt

# ===== 參數 =====
FS = 20.0
CUTOFF = 0.8
ORDER = 4

# 測試函式
def echo_test(input_str):
    return f"Python 收到: {input_str}"

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

# ===== 主流程（同步函式，Java 可直接呼叫）=====
def analyze_csv(file_path: str) -> dict:
    """
    讀取 CSV，進行低通 + 基線扣除 + 零交叉檢測
    回傳 dict，Java 端可取 .toString()
    """
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            df = pd.DataFrame(csv.DictReader(f))
        lowmap = {str(c).strip().lower(): c for c in df.columns if c is not None}

        # 檢查必要欄位
        if "time_seconds" not in lowmap or "outer_mouth_z_avg" not in lowmap:
            return {"status": "ERROR", "error": "缺少必要欄位"}

        # 數據轉 numpy
        t_raw = pd.to_numeric(df[lowmap["time_seconds"]], errors="coerce").to_numpy()
        r_raw = pd.to_numeric(df[lowmap["outer_mouth_z_avg"]], errors="coerce").to_numpy()
        m = np.isfinite(t_raw) & np.isfinite(r_raw)
        t, r = t_raw[m], r_raw[m]
        # 輸出logcat
        print("[PYTHON DEBUG] t[:10] =", t[:10])  # 只印前 10 筆
        print("[PYTHON DEBUG] r[:10] =", r[:10])


        if len(t) < 2:
            return {"status": "OK", "action_count": 0, "total_action_time": 0.0,
                    "breakpoints": [], "segments": [], "debug": {"note": "insufficient data"}}

        # 低通
        r_filt = lowpass_filter(r, fs=FS, cutoff=CUTOFF, order=ORDER)
        # 輸出logcat
        print("[PYTHON DEBUG] r_filt =", r_filt[:20])  # 只印前 20 筆避免爆炸

        # 基線扣除
        win = int(4.0 * FS)
        baseline = moving_average(r_filt, win)
        r_detrend = r_filt - baseline
        # 輸出logcat
        print("[PYTHON DEBUG] r_detrend =", r_detrend[:20])  # 只印前 20 筆避免爆炸

        # 零交叉
        deadband = 0.005 * float(np.std(r_detrend)) if np.std(r_detrend) > 0 else 0.0
        min_interval = int(0.5 * FS)
        zc_all, zc_up, zc_down = zero_crossings(r_detrend, t, deadband=deadband, min_interval=min_interval)
        print("[PYTHON DEBUG] zc_all, zc_up, zc_down =", zc_all, zc_up, zc_down)

        # 建 segments
        segments, positive_segments = [], []
        if len(zc_all) >= 2:
            for i, (s, e) in enumerate(zip(zc_all[:-1], zc_all[1:])):
                st, ed = float(t[s]), float(t[e])
                dur = round(ed - st, 3)
                seg = {"index": i, "start_time": round(st, 3), "end_time": round(ed, 3), "duration": dur}
                segments.append(seg)
                if r_detrend[s] >= 0:
                    positive_segments.append(seg)

        breakpoints = [seg["end_time"] for seg in segments]
        total_action_time = round(sum(seg["duration"] for seg in positive_segments), 3)

        return {
            "status": "OK",
            "action_count": len(positive_segments),
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
