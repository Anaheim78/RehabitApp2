import io, csv
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt

# ===== 自動計算 FS =====
def calculate_fs_from_csv(file_path: str) -> float:
    """
    統計CSV中排除頭尾後,穩定區的最低幀數作為FS
    """
    df = pd.read_csv(file_path)
    df = df[df["state"] == "MAINTAINING"]

    if len(df) < 2:
        return 10.0  # 預設值

    t = pd.to_numeric(df["time_seconds"], errors="coerce").to_numpy()
    t = t[np.isfinite(t)]

    if len(t) < 2:
        return 10.0

    # 統計每秒的幀數
    sec_counts = {}
    for ti in t:
        sec = int(ti)
        sec_counts[sec] = sec_counts.get(sec, 0) + 1

    # 排除頭尾
    all_secs = sorted(sec_counts.keys())
    if len(all_secs) <= 2:
        # 太短就用全部
        stable_counts = list(sec_counts.values())
    else:
        # 排除第一秒和最後一秒
        stable_secs = all_secs[1:-1]
        stable_counts = [sec_counts[s] for s in stable_secs]

    if not stable_counts:
        return 10.0

    # 取穩定區的最低幀數
    min_fps = min(stable_counts)
    print(f"📊 自動計算 FS = {min_fps} (穩定區最低幀數)")
    return float(min_fps)

# ===== 判斷 nosepeak_direction 的主要狀態 =====
def get_dominant_nosepeak_direction(file_path: str) -> str:
    """
    只看校正階段 (CALIBRATING) 倒數兩秒的 nosepeak_direction
    """
    df = pd.read_csv(file_path)
    df_calib = df[df["state"] == "CALIBRATING"]

    if len(df_calib) == 0:
        print("⚠️  找不到 CALIBRATING 階段,預設使用 T")
        return "T"

    if "nosepeak_direction" not in df.columns:
        print("⚠️  找不到 nosepeak_direction 欄位,預設使用 T")
        return "T"

    # 取校正階段的最後兩秒
    t_calib = pd.to_numeric(df_calib["time_seconds"], errors="coerce").to_numpy()
    t_calib = t_calib[np.isfinite(t_calib)]

    if len(t_calib) == 0:
        print("⚠️  校正階段時間異常,預設使用 T")
        return "T"

    max_time = t_calib.max()
    threshold = max_time - 2.0  # 倒數兩秒

    # 篩選倒數兩秒的資料
    df_last2sec = df_calib[pd.to_numeric(df_calib["time_seconds"], errors="coerce") >= threshold]

    if len(df_last2sec) == 0:
        print("⚠️  倒數兩秒沒有資料,預設使用 T")
        return "T"

    # 統計 T 和 F
    counts = df_last2sec["nosepeak_direction"].value_counts().to_dict()
    t_count = counts.get("T", 0)
    f_count = counts.get("F", 0)

    dominant = "T" if t_count >= f_count else "F"
    print(f"📌 校正倒數2秒: T={t_count}, F={f_count} → 使用 {dominant}")
    return dominant

# ===== 參數 =====
CUTOFF = 0.8
ORDER = 4

# ===== 低通濾波器 =====
def lowpass_filter(x, fs, cutoff=CUTOFF, order=ORDER):
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
    讀取 CSV，自動計算 FS，根據 nosepeak_direction 選擇正/負半週
    """
    try:
        # 1. 自動計算 FS
        fs = calculate_fs_from_csv(file_path)

        # 2. 判斷 nosepeak_direction (只看校正階段倒數2秒)
        dominant_direction = get_dominant_nosepeak_direction(file_path)

        # 3. 讀取數據
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

        # 4. 低通
        r_filt = lowpass_filter(r, fs=fs, cutoff=CUTOFF, order=ORDER)

        # 5. 基線扣除
        win = int(4.0 * fs)
        baseline = moving_average(r_filt, win)
        r_detrend = r_filt - baseline

        # 6. 零交叉
        deadband = 0.001 * float(np.std(r_detrend)) if np.std(r_detrend) > 0 else 0.0
        min_interval = int(0.2 * fs)
        zc_all, zc_up, zc_down = zero_crossings(r_detrend, t, deadband=deadband, min_interval=min_interval)

        # 7. 建 segments，根據 nosepeak_direction 選擇正/負半週
        segments = []
        if len(zc_all) >= 2:
            for i, (s, e) in enumerate(zip(zc_all[:-1], zc_all[1:])):
                st, ed = float(t[s]), float(t[e])
                dur = round(ed - st, 3)

                avg_val = np.mean(r_detrend[s:e])

                # T: 取負半週 (嘴巴往前, Z變小)
                # F: 取正半週 (嘴巴往前, Z變大)
                if dominant_direction == "T":
                    if avg_val < 0:
                        segments.append({
                            "index": i,
                            "start_time": round(st, 3),
                            "end_time": round(ed, 3),
                            "duration": dur
                        })
                else:  # F
                    if avg_val > 0:
                        segments.append({
                            "index": i,
                            "start_time": round(st, 3),
                            "end_time": round(ed, 3),
                            "duration": dur
                        })

        # 8. 篩選動作
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
                "fs_hz": fs,
                "cutoff": CUTOFF,
                "order": ORDER,
                "nosepeak_direction": dominant_direction,
                "zc_all": len(zc_all),
                "zc_up": len(zc_up),
                "zc_down": len(zc_down),
                "deadband": round(deadband, 6),
                "min_interval": min_interval
            }
        }
    except Exception as e:
        import traceback
        return {"status": "ERROR", "error": str(e), "traceback": traceback.format_exc()}


# ===== 測試 =====
if __name__ == "__main__":
    file_path = "FaceTraining_POUT_LIPS_20251023_103227.csv"
    result = analyze_csv(file_path)
    print("\n結果:")
    print(f"動作數: {result.get('action_count', 0)}")
    print(f"總動作時間: {result.get('total_action_time', 0)}")
    print(f"斷點: {result.get('breakpoints', [])}")
    print(f"\nDebug: {result.get('debug', {})}")