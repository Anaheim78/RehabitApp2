import io
import csv
import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt

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
def analyze_high_peaks(i, zc_all, r_detrend, t, threshold, spans):
    """分析正半波，計算峰值與相鄰谷值的差值"""
    s, e = zc_all[i], zc_all[i + 1]
    seg = r_detrend[s:e]

    peak_val = np.max(seg)
    peak_idx = np.argmax(seg)
    peak_time = t[zc_all[i] + peak_idx]

    # 計算與前後谷值的差值
    prev_min = np.min(r_detrend[zc_all[i-1]:zc_all[i]]) if i - 1 >= 0 else np.nan
    next_min = np.min(r_detrend[zc_all[i+1]:zc_all[i+2]]) if i + 2 < len(zc_all) else np.nan

    diffs = []
    if np.isfinite(prev_min):
        diffs.append(peak_val - prev_min)
    if np.isfinite(next_min):
        diffs.append(peak_val - next_min)

    if any(d >= threshold for d in diffs):
        diff_max = max(diffs)
        st, ed = t[s], t[e]
        spans.append({
            "start_time": round(st, 3),
            "end_time": round(ed, 3),
            "peak_time": round(peak_time, 3),
            "peak_value": round(peak_val, 6),
            "diff_max": round(diff_max, 6),
            "duration": round(ed - st, 3)
        })
    return spans

# ===== 半波分析：負半波（谷值）=====
def analyze_low_troughs(i, zc_all, r_detrend, t, threshold, spans):
    """分析負半波，計算谷值與相鄰峰值的差值"""
    s, e = zc_all[i], zc_all[i + 1]
    seg = r_detrend[s:e]

    trough_val = np.min(seg)
    trough_idx = np.argmin(seg)
    trough_time = t[zc_all[i] + trough_idx]

    # 計算與前後峰值的差值
    prev_max = np.max(r_detrend[zc_all[i-1]:zc_all[i]]) if i - 1 >= 0 else np.nan
    next_max = np.max(r_detrend[zc_all[i+1]:zc_all[i+2]]) if i + 2 < len(zc_all) else np.nan

    diffs = []
    if np.isfinite(prev_max):
        diffs.append(prev_max - trough_val)
    if np.isfinite(next_max):
        diffs.append(next_max - trough_val)

    if any(d >= threshold for d in diffs):
        diff_max = max(diffs)
        st, ed = t[s], t[e]
        spans.append({
            "start_time": round(st, 3),
            "end_time": round(ed, 3),
            "trough_time": round(trough_time, 3),
            "trough_value": round(trough_val, 6),
            "diff_max": round(diff_max, 6),
            "duration": round(ed - st, 3)
        })
    return spans

# ===== 主分析流程 =====
def analyze_csv(file_path: str, cutoff: float = 0.8, order: int = 4,
                threshold: float = 0.0008, dir_default: str = "N") -> dict:
    """
    分析 CSV 檔案，自動判斷方向並計算動作次數

    Parameters:
    -----------
    file_path : str
        CSV 檔案路徑
    cutoff : float
        低通濾波截止頻率 (Hz)
    order : int
        濾波器階數
    threshold : float
        動作閾值（半波差值）
    dir_default : str
        預設方向 ("P" 或 "N")，當無法從 DEMO 判斷時使用

    Returns:
    --------
    dict : 包含分析結果的字典
    """
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

        if len(t) < 2:
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

        # 自動計算取樣率
        fs = calculate_fs_from_csv(file_path)

        # 濾波
        r_filt = lowpass_filter(r, fs=fs, cutoff=cutoff, order=order)

        # 基線扣除
        baseline = moving_average(r_filt, int(4.0 * fs))
        r_detrend = r_filt - baseline

        # 零交叉檢測
        zc_all = zero_crossings(r_detrend, t, deadband=0.0, min_interval=int(0.2 * fs))

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
                spans = analyze_high_peaks(i, zc_all, r_detrend, t, threshold, spans)
            elif dir_eff == "N" and seg_mean < 0:
                spans = analyze_low_troughs(i, zc_all, r_detrend, t, threshold, spans)

        # 計算總動作時間
        total_action_time = round(sum(action["duration"] for action in spans), 3)

        # 建立斷點列表
        breakpoints = [action["end_time"] for action in spans]

        # 轉換成 segments 格式（與 Java 期望的格式一致）
        segments = []
        for i, action in enumerate(spans):
            segments.append({
                "index": i,
                "start_time": action["start_time"],
                "end_time": action["end_time"],
                "duration": action["duration"]
            })

        # 返回與 count_sip_lips.py 完全相同的格式
        return {
            "status": "OK",
            "action_count": len(spans),
            "total_action_time": total_action_time,
            "breakpoints": breakpoints,
            "segments": segments,
            "debug": {
                "fs_hz": fs,
                "cutoff": cutoff,
                "order": order,
                "zc_all": len(zc_all),
                "zc_up": 0,  # 嘟嘴/抿嘴不區分上下交叉，統一給 0
                "zc_down": 0,
                "deadband": 0.0,  # 嘟嘴/抿嘴沒使用 deadband，統一給 0.0
                "min_interval": int(0.2 * fs)
            }
        }

    except Exception as e:
        import traceback
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


# ===== 測試 =====
if __name__ == "__main__":
    # 測試範例
    file_path = "FaceTraining_SIP_LIPS_20251029_131942_blue.csv"

    result = analyze_csv(file_path, cutoff=0.8, order=4, threshold=0.0008)

    print("\n===== 分析結果 =====")
    print(f"狀態: {result.get('status')}")
    print(f"動作次數: {result.get('action_count')}")
    print(f"總動作時間: {result.get('total_action_time')} 秒")
    print(f"\n斷點時間: {result.get('breakpoints', [])}")

    if result.get('segments'):
        print(f"\n===== 動作明細 =====")
        for seg in result.get('segments', []):
            print(f"動作 {seg['index']}:")
            print(f"  時間範圍: {seg['start_time']} ~ {seg['end_time']} 秒")
            print(f"  持續時間: {seg['duration']} 秒")

    print(f"\n===== Debug 資訊 =====")
    for key, value in result.get('debug', {}).items():
        print(f"{key}: {value}")