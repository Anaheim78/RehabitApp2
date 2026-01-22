# -*- coding: utf-8 -*-
import numpy as np
import pandas as pd
from scipy.ndimage import uniform_filter1d  # ⭐ 新增：移動平均濾波

def analyze_tongue_csv(
    file_path: str,
    direction: str ,                 # 必填： "上" | "下" | "左" | "右"（也接受 "up"/"down"/"left"/"right"）
    min_interval_sec: float = 0.5,  # 兩段最小間隔（秒）
    max_interp_frames: int = 10,    # 線性插值最多連補幾幀
    deadband_ratio: float = 0.001,  # 死區＝deadband_ratio * std
    filter_window: int = 5,         # ⭐ 新增：濾波窗口（1=不濾波）
    merge_gap_sec: float = 0.8      # ⭐ 新增：間隔小於此秒數就合併（0=不合併）
) -> dict:
    """
    回傳：
    {
      "status": "OK",
      "action_count": int,
      "total_action_time": float,
      "breakpoints": [t_end, ...],
      "segments": [{"index","start_time","end_time","duration"}, ...],
      "debug": {...}
    }
    """
    try:
        df = pd.read_csv(file_path)

        # 只留 MAINTAINING（若有）
        if "state" in df.columns:
            df = df[df["state"] == "MAINTAINING"].copy()

        # ---- 欄位檢查 ----
        base_need = ["time_seconds", "eyeL_x", "eyeL_y", "eyeR_x", "eyeR_y"]
        d = direction.strip().lower()
        if d in ( "下", "down"):
            need = base_need + ["bbox_bottom"]
        elif d in ("上", "up"):
            need = base_need + ["bbox_top"]
        elif d in ("右", "right"):
            need = base_need + ["bbox_right", "browC_x", "nose_x"]
        elif d in ("左", "left"):
            need = base_need + ["bbox_left", "browC_x", "nose_x"]
        else:
            return {"status": "ERROR", "error": f"unknown direction '{direction}'"}

        miss = [c for c in need if c not in df.columns]
        if miss:
            return {"status": "ERROR", "error": f"missing columns: {miss}"}

        # ---- 時間與基準點 ----
        t = df["time_seconds"].astype(float).to_numpy()
        if len(t) < 2:
            return {
                "status": "OK", "action_count": 0, "total_action_time": 0.0,
                "breakpoints": [], "segments": [],
                "debug": {"note": "insufficient rows"}
            }

        ym = ((df["eyeL_y"] + df["eyeR_y"]) / 2.0).astype(float).to_numpy()

        # ---- 依方向取訊號 s（把 -1 視為缺值）----
        if d in ("下", "down"):
            bbox = df["bbox_bottom"].astype(float).replace(-1, np.nan).to_numpy()
            s = bbox - ym
            want_negative = False                  # 下：看正向
        elif d in ("上", "up"):
            bbox = df["bbox_top"].astype(float).replace(-1, np.nan).to_numpy()
            s = bbox - ym
            want_negative = True                   # 上：看負向

        elif d in ("右", "right"):
            xmid = ((df["browC_x"] + df["nose_x"]) / 2.0).astype(float).to_numpy()
            bbox = df["bbox_right"].astype(float).replace(-1, np.nan).to_numpy()
            s = bbox - xmid
            want_negative = False                  # 右：看正向
        else:  # 左 / left
            xmid = ((df["browC_x"] + df["nose_x"]) / 2.0).astype(float).to_numpy()
            bbox = df["bbox_left"].astype(float).replace(-1, np.nan).to_numpy()
            s = bbox - xmid
            want_negative = True                   # 左：看負向

        # 若有 tongue_detected，0 視為缺值
        if "tongue_detected" in df.columns:
            det = df["tongue_detected"].astype(float).to_numpy()
            s[det == 0] = np.nan

        # ---- 線性插值（限制最長連續補點數）----
        s = pd.Series(s, dtype="float64").interpolate(
            method="linear", limit=max_interp_frames, limit_direction="both"
        ).to_numpy()

        #
        baseline = np.nanmedian(s)
        s = np.where(np.isnan(s), baseline, s)

        # # ⭐⭐⭐ 新增：移動平均濾波 ⭐⭐⭐
        # if filter_window > 1:
        #     nan_mask = np.isnan(s)
        #     s_for_filter = np.where(nan_mask, 0, s)
        #     s = uniform_filter1d(s_for_filter, size=filter_window)
        #     s[nan_mask] = np.nan

        # ✅ 改成（NaN 已填完，不用處理）
        if filter_window > 1:
            s = uniform_filter1d(s, size=filter_window)
        # # ⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐

        # 去中位數基線
        # s0 = s - np.nanmedian(s)
        s0 = s - baseline

        # 估取樣率（秒轉幀）
        dt = np.diff(t)
        dt = dt[np.isfinite(dt) & (dt > 0)]
        fs = float(1.0 / np.median(dt)) if dt.size else 20.0
        min_int_frames = int(round(min_interval_sec * fs))

        # deadband
        std = float(np.nanstd(s0)) if np.isfinite(s0).any() else 0.0
        db = deadband_ratio * std if std > 0 else 0.0

        # 目標區（方向 + 死區）
        if want_negative:
            target = (s0 < -db)
        else:
            target = (s0 > db)
        valid = np.isfinite(s0)
        target &= valid

        # ---- 以連續 target 區切段；套最小間隔；尾端補齊 ----
        segments = []
        in_seg = False
        last_end_idx = -10**9
        N = len(s0)

        def push_seg(si, ei):
            seg = {
                "index": len(segments),
                "start_time": round(float(t[si]), 3),
                "end_time":   round(float(t[ei]), 3),
                "duration":   round(float(t[ei] - t[si]), 3),
            }
            segments.append(seg)

        i = 0
        while i < N:
            if not in_seg:
                if target[i] and (i - last_end_idx) >= min_int_frames:
                    start_i = i
                    in_seg = True
            else:
                if not target[i]:
                    end_i = i - 1 if i > 0 else i
                    push_seg(start_i, end_i)
                    last_end_idx = end_i
                    in_seg = False
            i += 1

        if in_seg:  # 尾端補齊到最後有效點
            end_i = np.max(np.flatnonzero(valid)) if np.any(valid) else (N - 1)
            push_seg(start_i, end_i)

        # ⭐⭐⭐ 新增：合併間隔過短的段落 ⭐⭐⭐
        if len(segments) > 1 and merge_gap_sec > 0:
            merged = [segments[0].copy()]
            for seg in segments[1:]:
                gap = seg["start_time"] - merged[-1]["end_time"]
                if gap < merge_gap_sec:
                    merged[-1]["end_time"] = seg["end_time"]
                    merged[-1]["duration"] = round(
                        merged[-1]["end_time"] - merged[-1]["start_time"], 3
                    )
                else:
                    merged.append(seg.copy())
            for idx, seg in enumerate(merged):
                seg["index"] = idx
            segments = merged
        # ⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐⭐

        action_count = len(segments)
        total_action_time = round(sum(seg["duration"] for seg in segments), 3)
        breakpoints = [seg["end_time"] for seg in segments]

        return {
            "status": "OK",
            "action_count": action_count,
            "total_action_time": total_action_time,
            "breakpoints": breakpoints,
            "segments": segments,
            "debug": {
                "fs_hz": round(fs, 3),
                "deadband": round(db, 6),
                "min_interval_frames": min_int_frames,
                "direction": direction,
                "want_negative": want_negative,
                "max_interp_frames": max_interp_frames,
                "filter_window": filter_window,      # ⭐ 新增
                "merge_gap_sec": merge_gap_sec       # ⭐ 新增
            }
        }
    except Exception as e:
        return {"status": "ERROR", "error": str(e)}
