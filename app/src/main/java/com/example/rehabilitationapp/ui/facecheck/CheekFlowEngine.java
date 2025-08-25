package com.example.rehabilitationapp.ui.facecheck;

import android.graphics.Bitmap;
import android.util.Pair;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * CheekFlowEngine (補償版)
 * - 只輸出左右 Inner 平均光流 (u,v)
 * - 可選不降採樣 (targetWidth<=0)
 * - 剛性補償：以眼眶+鼻樑的中位數流量當頭部運動，從臉頰扣掉
 * - 指數平滑：出參做 EMA
 */
public class CheekFlowEngine {

    /** 可調參數 */
    public static class Params {
        public int targetWidth = 360;   // <=0 表示不降採樣
        public int flowEvery   = 2;     // 幀節流：每 N 幀算一次

        // Farneback 參數
        public double pyrScale = 0.5;
        public int    levels   = 3;
        public int    winSize  = 15;
        public int    iterations = 3;
        public int    polyN    = 5;
        public double polySigma= 1.2;
        public int    flags    = 0;

        // landmarks 是否為 0~1 正規化（相對 Bitmap）
        public boolean landmarksAreNormalized01 = true;

        // 剛性補償與平滑
        public boolean enableRigidCompensation = true; // ✅ 預設開啟
        public float   smoothAlpha = 0.25f;            // 0=不平滑；建議 0.2~0.4

        // 取樣上限（做中位數）
        public int     maxSamplesForMedian = 4096;
    }

    /** 只留 Inner */
    public enum Region { LEFT_INNER, RIGHT_INNER }

    /** Inner 群索引 */
    private static final int[] LEFT_INNER  = {1,98,97,164,0,37,39,40,186,216,205,203};
    private static final int[] RIGHT_INNER = {1,164,0,267,269,270,410,436,426,423,327,326};

    /** 剛性區（雙眼外圈 + 少量鼻樑點） */
    private static final int[] RIGID_IDX = concat(
            // Left eye outer ring
            new int[]{33,7,163,144,145,153,154,155,133,173,157,158,159,160,161,246},
            // Right eye outer ring
            new int[]{263,249,390,373,374,380,381,382,362,398,384,385,386,387,388,466},
            // Nose bridge few points
            new int[]{1,2,98,197,195,5,4,6}
    );

    private final Params p;
    private Mat  prevGray;
    private Size flowSize;
    private float scaleX = 1f, scaleY = 1f;
    private int frameGate = 0;

    // 上一次有效值（掉幀回傳）
    private final EnumMap<Region, Point> lastValid = new EnumMap<>(Region.class);
    // EMA 平滑
    private final EnumMap<Region, Point> lastSmooth = new EnumMap<>(Region.class);

    public CheekFlowEngine(Params params) {
        this.p = (params != null) ? params : new Params();
        for (Region r : Region.values()) {
            lastValid.put(r, new Point(0,0));
            lastSmooth.put(r, new Point(0,0));
        }
    }

    /** 結果 */
    public static class FlowResult {
        public long timestampMs;
        public EnumMap<Region, Point> vectors = new EnumMap<>(Region.class); // LI、RI
        public EnumMap<Region, Point> rawVectors = new EnumMap<>(Region.class);  // 原始
        public boolean computedThisFrame;
    }

    /** 主流程：傳入 Bitmap + landmarks(0~1 或像素)，回傳左右 Inner 平均 (u,v) */
    public FlowResult process(Bitmap bmp, float[][] landmarks, long tsMs) {
        FlowResult out = new FlowResult();
        out.timestampMs = tsMs;

        // 幀節流：非計算幀 → 回上一筆平滑後值
        if ((frameGate++ % p.flowEvery) != 0) {
            out.computedThisFrame = false;
            for (Region r : Region.values()) out.vectors.put(r, lastSmooth.get(r));
            return out;
        }

        // A) 影像→灰階（可選降採樣）
        Mat graySmall = toGrayDown(bmp);

        // B) Farneback 光流（在降採樣座標系）
        Pair<Mat, Mat> uv = computeFlow(graySmall);
        Mat flowU = uv.first, flowV = uv.second;

        // C) 建 Inner ROI mask（原圖尺寸）
        Size origSize = new Size(bmp.getWidth(), bmp.getHeight());
        Mat maskLI = buildPolyMask(origSize, landmarks, LEFT_INNER);
        Mat maskRI = buildPolyMask(origSize, landmarks, RIGHT_INNER);

        // D) 剛性補償：眼眶+鼻樑 凸包 → 中位數流
        Point rigidMed = new Point(0,0);
        if (p.enableRigidCompensation) {
            Mat rigidMask = buildHullMask(origSize, landmarks, RIGID_IDX);
            rigidMed = medianFlowInMask(flowU, flowV, rigidMask);
            rigidMask.release();
            if (Double.isNaN(rigidMed.x) || Double.isNaN(rigidMed.y)) rigidMed = new Point(0,0);
        }

        // E) 各區流量（中位數，較抗 outlier）
        Point vLI = medianFlowInMask(flowU, flowV, maskLI);
        Point vRI = medianFlowInMask(flowU, flowV, maskRI);

        // 存 rawVectors（補償前）
        out.rawVectors.put(Region.LEFT_INNER, vLI);
        out.rawVectors.put(Region.RIGHT_INNER, vRI);

        // F) 尺度換回原圖，並做剛性補償
        vLI = scaleAndCompensate(vLI, rigidMed);
        vRI = scaleAndCompensate(vRI, rigidMed);

        // G) NaN 防護 + EMA 平滑
        vLI = fixNaN(Region.LEFT_INNER, vLI);
        vRI = fixNaN(Region.RIGHT_INNER, vRI);

        vLI = smooth(Region.LEFT_INNER, vLI);
        vRI = smooth(Region.RIGHT_INNER, vRI);

        out.vectors.put(Region.LEFT_INNER,  vLI);
        out.vectors.put(Region.RIGHT_INNER, vRI);
        out.computedThisFrame = true;

        // 釋放
        flowU.release(); flowV.release();
        maskLI.release(); maskRI.release();
        graySmall.release();

        return out;
    }

    // ====== 影像 → 灰階降採樣（targetWidth<=0 則不縮） ======
    private Mat toGrayDown(Bitmap bmp) {
        Mat rgba = new Mat();
        Utils.bitmapToMat(bmp, rgba);
        Mat gray = new Mat();
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
        rgba.release();

        if (p.targetWidth <= 0) {
            if (flowSize == null) {
                flowSize = new Size(gray.width(), gray.height());
                scaleX = scaleY = 1f;
            }
            return gray;
        }

        if (flowSize == null) {
            flowSize = computeFlowSize(gray.width(), gray.height(), p.targetWidth);
            scaleX = (float) (gray.width()  / flowSize.width);
            scaleY = (float) (gray.height() / flowSize.height);
        }
        if (gray.width() != flowSize.width || gray.height() != flowSize.height) {
            Mat small = new Mat();
            Imgproc.resize(gray, small, flowSize, 0, 0, Imgproc.INTER_AREA);
            gray.release();
            return small;
        }
        return gray;
    }

    private Size computeFlowSize(int w, int h, int tw) {
        if (tw <= 0 || w <= tw) return new Size(w, h);
        double s = tw / (double) w;
        return new Size(tw, (int) Math.round(h * s));
    }

    // ====== Farneback（安全版：欄位快照 + 初幀回單通道 0 流 + 安全更新）======
    private Pair<Mat, Mat> computeFlow(Mat currGray) {
        // 0) 入口防呆：避免空幀
        if (currGray == null || currGray.empty()) {
            Mat u0 = Mat.zeros(new Size(1,1), CvType.CV_32F);
            Mat v0 = Mat.zeros(new Size(1,1), CvType.CV_32F);
            return new Pair<>(u0, v0);
        }

        // 1) 抓欄位快照（避免其他執行緒中途把 this.prevGray 改掉）
        Mat prev = this.prevGray;  // 讀一次欄位
        Mat curr = currGray;       // 傳入參數；本方法不釋放它

        // 2) 第一幀：只初始化 prevGray，回兩張「單通道」零流（更直覺）
        if (prev == null || prev.empty()) {
            // 下一輪才開始算流
            if (this.prevGray != null && !this.prevGray.empty()) {
                this.prevGray.release();
            }
            this.prevGray = curr.clone();

            Mat u0 = Mat.zeros(curr.size(), CvType.CV_32F);
            Mat v0 = Mat.zeros(curr.size(), CvType.CV_32F);
            return new Pair<>(u0, v0);
        }

        Mat flow = new Mat();
        List<Mat> channels = new ArrayList<>(2);
        try {
            // 3) 算 2 通道光流（不直接用 this.prevGray / this.currGray，避免競態）
            Video.calcOpticalFlowFarneback(
                    prev, curr, flow,
                    p.pyrScale, p.levels, p.winSize, p.iterations, p.polyN, p.polySigma, p.flags);

            // 4) split 成 U/V（單通道）。注意：回傳給呼叫端，這兩個不要在這裡釋放
            Core.split(flow, channels); // channels.get(0)=U, get(1)=V

            // 5) 安全更新欄位 prevGray：先釋放舊的，再以「本幀 clone」覆蓋
            if (this.prevGray != null && !this.prevGray.empty()) {
                this.prevGray.release();
            }
            this.prevGray = curr.clone();

            // 6) 回傳 U/V：由呼叫端負責 release
            return new Pair<>(channels.get(0), channels.get(1));

        } finally {
            // 7) 釋放本方法建立的暫存物件（flow）；channels 交由呼叫端釋放
            if (flow != null) flow.release();
        }
    }

    // ====== 建 polygon mask（原圖尺寸） ======
    private Mat buildPolyMask(Size frameSize, float[][] landmarks, int[] idx) {
        int W = (int) frameSize.width;
        int H = (int) frameSize.height;
        Mat mask = Mat.zeros(H, W, CvType.CV_8UC1);

        Point[] pts = new Point[idx.length];
        for (int i = 0; i < idx.length; i++) {
            float x = landmarks[idx[i]][0];
            float y = landmarks[idx[i]][1];
            if (p.landmarksAreNormalized01) { x *= W; y *= H; }
            pts[i] = new Point(x, y);
        }
        MatOfPoint poly = new MatOfPoint(pts);
        Imgproc.fillPoly(mask, Collections.singletonList(poly), new Scalar(255));
        poly.release();
        return mask;
    }

    // ====== 建凸包（剛性區） ======
    private Mat buildHullMask(Size frameSize, float[][] landmarks, int[] idx) {
        int W = (int) frameSize.width;
        int H = (int) frameSize.height;
        Mat mask = Mat.zeros(H, W, CvType.CV_8UC1);

        // 蒐集點
        List<Point> ptsList = new ArrayList<>(idx.length);
        for (int i : idx) {
            float x = landmarks[i][0], y = landmarks[i][1];
            if (p.landmarksAreNormalized01) { x *= W; y *= H; }
            ptsList.add(new Point(x, y));
        }
        if (ptsList.size() < 3) return mask;

        MatOfPoint mop = new MatOfPoint();
        mop.fromList(ptsList);

        // convex hull
        MatOfInt hullIdx = new MatOfInt();
        Imgproc.convexHull(mop, hullIdx);

        int[] hIdx = hullIdx.toArray();
        Point[] all = mop.toArray();
        Point[] hPts = new Point[hIdx.length];
        for (int i = 0; i < hIdx.length; i++) hPts[i] = all[hIdx[i]];

        MatOfPoint hull = new MatOfPoint(hPts);
        Imgproc.fillConvexPoly(mask, hull, new Scalar(255));

        // 釋放
        hullIdx.release();
        hull.release();
        mop.release();

        return mask;
    }

    // ====== 在 mask 內取「中位數」流（先在光流尺寸），最後會做尺度換回與補償 ======
    private Point medianFlowInMask(Mat flowU, Mat flowV, Mat roiMaskOrig) {
        // 轉到光流尺寸
        Mat roiSmall = new Mat();
        Imgproc.resize(roiMaskOrig, roiSmall, flowU.size(), 0, 0, Imgproc.INTER_NEAREST);

        int rows = flowU.rows();
        int cols = flowU.cols();
        int total = rows * cols;

        // 動態取樣間隔，最多取 p.maxSamplesForMedian 筆
        int step = Math.max(1, (int)Math.sqrt((double)total / Math.max(1, p.maxSamplesForMedian)));

        // 收集樣本
        ArrayList<Float> us = new ArrayList<>();
        ArrayList<Float> vs = new ArrayList<>();
        for (int y = 0; y < rows; y += step) {
            for (int x = 0; x < cols; x += step) {
                double m = roiSmall.get(y, x)[0];
                if (m > 0.5) {
                    float u = (float)flowU.get(y, x)[0];
                    float v = (float)flowV.get(y, x)[0];
                    if (!Float.isNaN(u) && !Float.isNaN(v) && !Float.isInfinite(u) && !Float.isInfinite(v)) {
                        us.add(u); vs.add(v);
                    }
                }
            }
        }
        roiSmall.release();

        if (us.isEmpty()) return new Point(Double.NaN, Double.NaN);

        Collections.sort(us);
        Collections.sort(vs);
        float mu = us.get(us.size()/2);
        float mv = vs.get(vs.size()/2);

        // 先回傳光流尺度；之後統一做 scale & compensate
        return new Point(mu, mv);
    }

    // 將光流尺度 → 原圖尺度，並做剛性補償
    private Point scaleAndCompensate(Point v, Point rigidMed) {
        double ux = v.x * scaleX;
        double uy = v.y * scaleY;

        if (p.enableRigidCompensation) {
            ux -= rigidMed.x * scaleX;
            uy -= rigidMed.y * scaleY;
        }
        return new Point(ux, uy);
    }

    // NaN 防護；同時更新 lastValid
    private Point fixNaN(Region r, Point v) {
        if (Double.isNaN(v.x) || Double.isNaN(v.y)) return lastValid.get(r);
        lastValid.put(r, v);
        return v;
    }

    // EMA 平滑
    private Point smooth(Region r, Point cur) {
        float a = p.smoothAlpha;
        if (a <= 0f) {
            lastSmooth.put(r, cur);
            return cur;
        }
        Point prev = lastSmooth.get(r);
        double sx = (1 - a) * prev.x + a * cur.x;
        double sy = (1 - a) * prev.y + a * cur.y;
        Point s = new Point(sx, sy);
        lastSmooth.put(r, s);
        return s;
    }

    public void release() {
        if (prevGray != null) { prevGray.release(); prevGray = null; }
    }

    // 小工具：concat 多個 int 陣列
    private static int[] concat(int[]... arrays) {
        int n = 0;
        for (int[] a : arrays) n += a.length;
        int[] out = new int[n];
        int k = 0;
        for (int[] a : arrays) {
            System.arraycopy(a, 0, out, k, a.length);
            k += a.length;
        }
        return out;
    }
}
