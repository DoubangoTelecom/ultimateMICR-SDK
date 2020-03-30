/*
 * Copyright (C) 2016-2020Doubango AI <https://www.doubango.org>
 * License: For non-commercial use only
 * Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
 * WebSite: https://www.doubango.org/webapps/micr/
 */
package org.doubango.ultimateMICR.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import org.doubango.ultimateMicr.Sdk.UltMicrSdkResult;

import java.util.List;

public class MICRView extends View {

    static final String TAG = MICRView.class.getCanonicalName();

    static final float TEXT_NUMBER_SIZE_DIP = 12;
    static final float TEXT_CONFIDENCE_SIZE_DIP = 10;
    static final float TEXT_PROCESSING_TIME_SIZE_DIP = 10;
    static final int STROKE_WIDTH_DIP = 3;

    private final Typeface mFontE13B;
    private final Typeface mFontCMC7;

    private final Paint mPaintText;
    private final Paint mPaintTextBackground;
    private final Paint mPaintTextConfidence;
    private final Paint mPaintTextConfidenceBackground;
    private final Paint mPaintBorder;
    private final Paint mPaintTextProcessingTime;
    private final Paint mPaintTextProcessingTimeBackground;
    private final Paint mPaintDetectROI;

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    long mProcessingTimeMillis;

    private Size mImageSize;
    private List<MICRUtils.Zone> mZones = null;
    private RectF mDetectROI;

    /**
     *
     * @param context
     * @param attrs
     */
    public MICRView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final float strokeWidthInPixel = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, STROKE_WIDTH_DIP, getResources().getDisplayMetrics());

        mFontE13B = Typeface.createFromAsset(context.getAssets(), "e13b/micrenc.ttf");
        mFontCMC7 = Typeface.createFromAsset(context.getAssets(), "cmc7/Cmc7.ttf");

        mPaintText = new Paint();
        mPaintText.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_NUMBER_SIZE_DIP, getResources().getDisplayMetrics()));
        mPaintText.setColor(Color.BLACK);
        mPaintText.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaintTextBackground = new Paint();
        mPaintTextBackground.setColor(Color.YELLOW);
        mPaintTextBackground.setStrokeWidth(strokeWidthInPixel);
        mPaintTextBackground.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaintTextConfidence = new Paint();
        mPaintTextConfidence.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_CONFIDENCE_SIZE_DIP, getResources().getDisplayMetrics()));
        mPaintTextConfidence.setColor(Color.BLUE);
        mPaintTextConfidence.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaintTextConfidence.setTypeface(Typeface.create("Arial", Typeface.BOLD));

        mPaintTextConfidenceBackground = new Paint();
        mPaintTextConfidenceBackground.setColor(Color.YELLOW);
        mPaintTextConfidenceBackground.setStrokeWidth(strokeWidthInPixel);
        mPaintTextConfidenceBackground.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaintBorder = new Paint();
        mPaintBorder.setStrokeWidth(strokeWidthInPixel);
        mPaintBorder.setPathEffect(null);
        mPaintBorder.setColor(Color.YELLOW);
        mPaintBorder.setStyle(Paint.Style.STROKE);

        mPaintTextProcessingTime = new Paint();
        mPaintTextProcessingTime.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_PROCESSING_TIME_SIZE_DIP, getResources().getDisplayMetrics()));
        mPaintTextProcessingTime.setColor(Color.BLACK);
        mPaintTextProcessingTime.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaintTextProcessingTime.setTypeface(Typeface.create("Arial", Typeface.BOLD));

        mPaintTextProcessingTimeBackground = new Paint();
        mPaintTextProcessingTimeBackground.setColor(Color.WHITE);
        mPaintTextProcessingTimeBackground.setStrokeWidth(strokeWidthInPixel);
        mPaintTextProcessingTimeBackground.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaintDetectROI = new Paint();
        mPaintDetectROI.setColor(Color.RED);
        mPaintDetectROI.setStrokeWidth(strokeWidthInPixel);
        mPaintDetectROI.setStyle(Paint.Style.STROKE);
        mPaintDetectROI.setPathEffect(new DashPathEffect(new float[] {10,20}, 0));
    }

    public void setDetectROI(final RectF roi) { mDetectROI = roi; }

    /**
     *
     * @param width
     * @param height
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.i(TAG, "onMeasure");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    /**
     *
     * @param result
     * @param imageSize
     * @param processingTimeMillis
     */
    public synchronized void setResult(@NonNull final UltMicrSdkResult result, @NonNull final Size imageSize, final long processingTimeMillis) {
        mZones = MICRUtils.extractZones(result);
        mImageSize = imageSize;
        mProcessingTimeMillis = processingTimeMillis;
        postInvalidate();
    }

    @Override
    public synchronized void draw(final Canvas canvas) {
        super.draw(canvas);

        if (mImageSize == null) {
            Log.i(TAG, "Not initialized yet");
            return;
        }

        // Total processing time (Inference, format conversion, thresholding, binarization...)
        final String mInferenceTimeMillisString = "Total processing time: " + mProcessingTimeMillis;
        Rect boundsTextProcessingTimeMillis = new Rect();
        mPaintTextProcessingTime.getTextBounds(mInferenceTimeMillisString, 0, mInferenceTimeMillisString.length(), boundsTextProcessingTimeMillis);
        canvas.drawRect(0, 0, boundsTextProcessingTimeMillis.width(), boundsTextProcessingTimeMillis.height(), mPaintTextProcessingTimeBackground);
        canvas.drawText(mInferenceTimeMillisString, 0, boundsTextProcessingTimeMillis.height(), mPaintTextProcessingTime);

        // Transformation info
        final MICRUtils.MICRTransformationInfo tInfo = new MICRUtils.MICRTransformationInfo(mImageSize.getWidth(), mImageSize.getHeight(), getWidth(), getHeight());

        // ROI
        if (mDetectROI != null && !mDetectROI.isEmpty()) {
            canvas.drawRect(
                    new RectF(
                            tInfo.transformX(mDetectROI.left),
                            tInfo.transformY(mDetectROI.top),
                            tInfo.transformX(mDetectROI.right),
                            tInfo.transformY(mDetectROI.bottom)
                    ),
                    mPaintDetectROI
            );
        }

        // Zones
        if (mZones != null && !mZones.isEmpty()) {

            // We want the stoke to be outside of the text and this is why we use half-stroke width offset
            final float borderWidth = (mPaintBorder.getStrokeWidth() * 0.5f) * 3.f; // times 3.f to have nice visual effect

            for (final MICRUtils.Zone zone : mZones) {
                // Set typeface
                mPaintText.setTypeface(Typeface.create(
                        zone.getKlass() == MICRUtils.KLASS_CMC7 ? mFontCMC7 : mFontE13B,
                        Typeface.BOLD));
                // Transform corners
                final float[] warpedBox = zone.getWarpedBox();
                final PointF cornerA = new PointF(tInfo.transformX(warpedBox[0]), tInfo.transformY(warpedBox[1]));
                final PointF cornerB = new PointF(tInfo.transformX(warpedBox[2]), tInfo.transformY(warpedBox[3]));
                final PointF cornerC = new PointF(tInfo.transformX(warpedBox[4]), tInfo.transformY(warpedBox[5]));
                final PointF cornerD = new PointF(tInfo.transformX(warpedBox[6]), tInfo.transformY(warpedBox[7]));
                // Draw border
                final Path pathBorder = new Path();
                pathBorder.moveTo(cornerA.x, cornerA.y);
                pathBorder.lineTo(cornerB.x, cornerB.y);
                pathBorder.lineTo(cornerC.x, cornerC.y);
                pathBorder.lineTo(cornerD.x, cornerD.y);
                pathBorder.lineTo(cornerA.x, cornerA.y);
                pathBorder.close();
                canvas.drawPath(pathBorder, mPaintBorder);

                // Draw text number
                String text = zone.getText();
                if (zone.getKlass() == MICRUtils.KLASS_CMC7) {
                    // Text substitution for the CMC7 font
                    text = text
                    .replaceAll("F", "[")
                    .replaceAll("G", "]")
                    .replaceAll("H", "{")
                    .replaceAll("I", "}")
                    .replaceAll("J", "|");
                }
                Rect boundsTextNumber = new Rect();
                mPaintText.getTextBounds(text, 0, text.length(), boundsTextNumber);
                final RectF rectTextNumber = new RectF(
                        cornerA.x,
                        cornerA.y - boundsTextNumber.height(),
                        cornerA.x + boundsTextNumber.width(),
                        cornerA.y
                );
                final Path pathText = new Path();
                pathText.moveTo(cornerA.x, cornerA.y);
                pathText.lineTo(Math.max(cornerB.x, (cornerA.x + rectTextNumber.width())), cornerB.y);
                pathText.addRect(rectTextNumber, Path.Direction.CCW);
                pathText.close();
                canvas.drawPath(pathText, mPaintTextBackground);
                canvas.drawTextOnPath(text, pathText, 0, 0, mPaintText);

                // Draw text confidence
                final String confidence = String.format("%.2f%%", Math.min(zone.getRecognitionConfidence(), zone.getDetectionConfidence()));
                Rect boundsTextConfidence = new Rect();
                mPaintTextConfidence.getTextBounds(confidence, 0, confidence.length(), boundsTextConfidence);
                final RectF rectTextConfidence = new RectF(
                        cornerD.x,
                        cornerD.y,
                        cornerD.x + boundsTextConfidence.width(),
                        cornerD.y + boundsTextConfidence.height()
                );
                final Path pathTextConfidence = new Path();
                final double dx = cornerC.x - cornerD.x;
                final double dy = cornerC.y - cornerD.y;
                final double angle = Math.atan2(dy, dx);
                final double cosT = Math.cos(angle);
                final double sinT = Math.sin(angle);
                final float Cx = cornerD.x + rectTextConfidence.width();
                final float Cy = cornerC.y;
                final PointF cornerCC = new PointF((float)(Cx * cosT - Cy * sinT), (float)(Cy * cosT + Cx * sinT));
                final PointF cornerDD = new PointF((float)(cornerD.x * cosT - cornerD.y * sinT), (float)(cornerD.y * cosT + cornerD.x * sinT));
                pathTextConfidence.moveTo(cornerDD.x, cornerDD.y + boundsTextConfidence.height());
                pathTextConfidence.lineTo(cornerCC.x, cornerCC.y + boundsTextConfidence.height());
                pathTextConfidence.addRect(rectTextConfidence, Path.Direction.CCW);
                pathTextConfidence.close();
                canvas.drawPath(pathTextConfidence, mPaintTextConfidenceBackground);
                canvas.drawTextOnPath(confidence, pathTextConfidence, 0, 0, mPaintTextConfidence);
            }
        }
    }
}