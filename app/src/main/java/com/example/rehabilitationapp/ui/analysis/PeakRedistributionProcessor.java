package com.example.rehabilitationapp.ui.analysis;

import android.util.Log;
import java.util.*;

/**
 * 峰值檢測與重分配處理器
 * 移植自Python EMD + 小波降噪 + 峰值重分配邏輯
 */
public class PeakRedistributionProcessor {
    private static final String TAG = "PeakProcessor";

    // 峰值檢測參數
    private static final int MIN_DISTANCE = 20;        // 峰值間最小距離
    private static final int CLUSTERING_DISTANCE = 40; // 聚類距離
    private static final double THRESHOLD_MULTIPLIER = 0.5; // 閾值倍數

    /**
     * 峰值群組資訊
     */
    public static class PeakGroup {
        public int groupId;
        public List<Integer> originalPeaks;
        public List<Double> originalHeights;
        public int newCenter;
        public double newHeight;
        public int peakCount;

        public PeakGroup(int groupId) {
            this.groupId = groupId;
            this.originalPeaks = new ArrayList<>();
            this.originalHeights = new ArrayList<>();
        }
    }

    /**
     * 處理結果
     */
    public static class ProcessingResult {
        public double[] redistributedSignal;
        public List<Integer> originalPeaks;
        public List<PeakGroup> clusterInfo;
        public int originalPeakCount;
        public int redistributedPeakCount;
        public double peakReductionRatio;
        public double energyPreservationRatio;

        public ProcessingResult(int signalLength) {
            this.redistributedSignal = new double[signalLength];
            this.originalPeaks = new ArrayList<>();
            this.clusterInfo = new ArrayList<>();
        }
    }

    /**
     * 主要處理方法：峰值檢測與重分配
     */
    public static ProcessingResult processSignal(double[] signal) {
        Log.d(TAG, "🎯 開始峰值檢測與重分配處理...");
        Log.d(TAG, "信號長度: " + signal.length);

        ProcessingResult result = new ProcessingResult(signal.length);

        // 1. 計算自動閾值
        double mean = calculateMean(signal);
        double std = calculateStandardDeviation(signal, mean);
        double threshold = mean + THRESHOLD_MULTIPLIER * std;

        Log.d(TAG, String.format("統計資訊 - 平均值: %.6f, 標準差: %.6f, 閾值: %.6f",
                mean, std, threshold));

        // 2. 峰值檢測
        result.originalPeaks = detectPeaks(signal, threshold, MIN_DISTANCE);
        result.originalPeakCount = result.originalPeaks.size();

        Log.d(TAG, "🔍 檢測到 " + result.originalPeakCount + " 個峰值");

        if (result.originalPeakCount == 0) {
            Log.d(TAG, "❌ 未檢測到峰值，返回空結果");
            return result;
        }

        // 3. 峰值聚類
        result.clusterInfo = clusterPeaks(result.originalPeaks, signal, CLUSTERING_DISTANCE);
        Log.d(TAG, "📊 峰值聚類結果: " + result.clusterInfo.size() + " 個群組");

        // 4. 能量重分配
        redistributeEnergy(result, signal);

        // 5. 計算統計資訊
        calculateStatistics(result, signal);

        Log.d(TAG, "✅ 峰值重分配完成!");
        Log.d(TAG, String.format("   - 原始峰值數: %d", result.originalPeakCount));
        Log.d(TAG, String.format("   - 重分配後群組數: %d", result.redistributedPeakCount));
        Log.d(TAG, String.format("   - 峰值減少率: %.1f%%", result.peakReductionRatio));
        Log.d(TAG, String.format("   - 能量保存率: %.1f%%", result.energyPreservationRatio));

        return result;
    }

    /**
     * 峰值檢測
     */
    private static List<Integer> detectPeaks(double[] signal, double threshold, int minDistance) {
        List<Integer> peaks = new ArrayList<>();

        for (int i = 1; i < signal.length - 1; i++) {
            // 檢查是否為局部最大值且超過閾值
            if (signal[i] > threshold &&
                    signal[i] > signal[i-1] &&
                    signal[i] > signal[i+1]) {

                // 檢查距離約束
                boolean validDistance = true;
                for (int existingPeak : peaks) {
                    if (Math.abs(i - existingPeak) < minDistance) {
                        // 如果新峰值更高，替換舊的
                        if (signal[i] > signal[existingPeak]) {
                            peaks.remove(peaks.indexOf(existingPeak));
                            break;
                        } else {
                            validDistance = false;
                            break;
                        }
                    }
                }

                if (validDistance) {
                    peaks.add(i);
                }
            }
        }

        // 按位置排序
        Collections.sort(peaks);
        return peaks;
    }

    /**
     * 峰值聚類
     */
    private static List<PeakGroup> clusterPeaks(List<Integer> peaks, double[] signal, int clusteringDistance) {
        List<PeakGroup> groups = new ArrayList<>();

        if (peaks.isEmpty()) {
            return groups;
        }

        List<Integer> currentGroup = new ArrayList<>();
        currentGroup.add(0); // 存儲peaks的索引

        for (int i = 1; i < peaks.size(); i++) {
            int currentPeakPos = peaks.get(i);
            int lastGroupPeakPos = peaks.get(currentGroup.get(currentGroup.size() - 1));

            if (currentPeakPos - lastGroupPeakPos <= clusteringDistance) {
                // 距離太近，歸入同一組
                currentGroup.add(i);
            } else {
                // 距離夠遠，完成當前組並開始新組
                PeakGroup group = createPeakGroup(groups.size() + 1, currentGroup, peaks, signal);
                groups.add(group);

                currentGroup = new ArrayList<>();
                currentGroup.add(i);
            }
        }

        // 處理最後一組
        if (!currentGroup.isEmpty()) {
            PeakGroup group = createPeakGroup(groups.size() + 1, currentGroup, peaks, signal);
            groups.add(group);
        }

        return groups;
    }

    /**
     * 創建峰值群組
     */
    private static PeakGroup createPeakGroup(int groupId, List<Integer> peakIndices,
                                             List<Integer> peaks, double[] signal) {
        PeakGroup group = new PeakGroup(groupId);

        double totalEnergy = 0;
        double weightedSum = 0;

        for (int peakIndex : peakIndices) {
            int peakPos = peaks.get(peakIndex);
            double peakHeight = signal[peakPos];

            group.originalPeaks.add(peakPos);
            group.originalHeights.add(peakHeight);

            totalEnergy += peakHeight;
            weightedSum += peakPos * peakHeight;
        }

        // 計算加權中心位置
        group.newCenter = (int) Math.round(weightedSum / totalEnergy);
        group.newCenter = Math.max(0, Math.min(signal.length - 1, group.newCenter));
        group.newHeight = totalEnergy;
        group.peakCount = peakIndices.size();

        Log.d(TAG, String.format("   - 群組 %d: %d 個峰值 → 位置 %d, 高度 %.6f",
                groupId, group.peakCount, group.newCenter, group.newHeight));

        return group;
    }

    /**
     * 能量重分配
     */
    private static void redistributeEnergy(ProcessingResult result, double[] signal) {
        Arrays.fill(result.redistributedSignal, 0.0);

        for (PeakGroup group : result.clusterInfo) {
            result.redistributedSignal[group.newCenter] = group.newHeight;
        }

        result.redistributedPeakCount = result.clusterInfo.size();
    }

    /**
     * 計算統計資訊
     */
    private static void calculateStatistics(ProcessingResult result, double[] signal) {
        // 計算峰值減少率
        if (result.originalPeakCount > 0) {
            result.peakReductionRatio = ((double)(result.originalPeakCount - result.redistributedPeakCount)
                    / result.originalPeakCount) * 100.0;
        } else {
            result.peakReductionRatio = 0.0;
        }

        // 計算能量保存率
        double originalEnergy = 0;
        for (int peakPos : result.originalPeaks) {
            originalEnergy += signal[peakPos];
        }

        double redistributedEnergy = 0;
        for (double value : result.redistributedSignal) {
            redistributedEnergy += value;
        }

        if (originalEnergy > 0) {
            result.energyPreservationRatio = (redistributedEnergy / originalEnergy) * 100.0;
        } else {
            result.energyPreservationRatio = 0.0;
        }
    }

    /**
     * 計算平均值
     */
    public static double calculateMean(double[] data) {
        double sum = 0;
        for (double value : data) {
            sum += value;
        }
        return sum / data.length;
    }

    /**
     * 計算標準差
     */
    public static double calculateStandardDeviation(double[] data, double mean) {
        double sumSquaredDiff = 0;
        for (double value : data) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        return Math.sqrt(sumSquaredDiff / data.length);
    }

    /**
     * 簡化版本：只返回峰值數量（用於快速分析）
     */
    public static int countPeaks(double[] signal) {
        double mean = calculateMean(signal);
        double std = calculateStandardDeviation(signal, mean);
        double threshold = mean + THRESHOLD_MULTIPLIER * std;

        List<Integer> peaks = detectPeaks(signal, threshold, MIN_DISTANCE);
        List<PeakGroup> groups = clusterPeaks(peaks, signal, CLUSTERING_DISTANCE);

        Log.d(TAG, String.format("快速峰值計數 - 原始: %d, 重分配後: %d",
                peaks.size(), groups.size()));

        return groups.size(); // 返回重分配後的峰值數量
    }
}