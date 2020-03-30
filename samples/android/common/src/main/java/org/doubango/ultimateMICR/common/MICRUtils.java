/*
 * Copyright (C) 2016-2020 Doubango AI <https://www.doubango.org>
 * License: For non-commercial use only
 * Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
 * WebSite: https://www.doubango.org/webapps/micr/
 */
package org.doubango.ultimateMICR.common;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.PointF;
import android.util.Log;

import org.doubango.ultimateMicr.Sdk.UltMicrSdkResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class
 */
public class MICRUtils {
    static final String TAG = MICRUtils.class.getCanonicalName();

    public static final int KLASS_E13B = 1; // Same as C++ code "kUltMicrDetectorResultKlassE13B"
    public static final int KLASS_CMC7 = 2; // Same as C++ code "kUltMicrDetectorResultKlassCMC7"

    /**
     * Translation and scaling information used to map image pixels to screen pixels.
     */
    public static class MICRTransformationInfo {
        final int mXOffset;
        final int mYOffset;
        final float mRatio;
        final int mWidth;
        final int mHeight;
        public MICRTransformationInfo(final int imageWidth, final int imageHeight, final int canvasWidth, final int canvasHeight) {
            final float xRatio = (float)canvasWidth / (float)imageWidth;
            final float yRatio =  (float)canvasHeight / (float)imageHeight;
            mRatio = Math.min( xRatio, yRatio );
            mWidth = (int)(imageWidth * mRatio);
            mHeight = (int)(imageHeight * mRatio);
            mXOffset = (canvasWidth - mWidth) >> 1;
            mYOffset = (canvasHeight - mHeight) >> 1;
        }
        public float transformX(final float x) { return x * mRatio + mXOffset; }
        public float transformY(final float y) { return y * mRatio + mYOffset; }
        public PointF transform(final PointF p) { return new PointF(transformX(p.x), transformY(p.y)); }
        public int getXOffset() { return mXOffset; }
        public int getYOffset() { return mYOffset; }
        public float getRatio() { return mRatio; }
        public int getWidth() { return mWidth; }
        public int getHeight() { return mHeight; }
    }

    /**
     * MICR zone
     */
    static class Zone {
        private int mKlass;
        private float mSkew;
        private String mText;
        private String mDescription;
        private float mDetectionConfidence;
        private float mRecognitionConfidence;
        private float mWarpedBox[];

        public int getKlass() { return mKlass; }
        public float getSkew() { return mSkew; }
        public String getText() { return mText; }
        public String getDescription() { return mDescription; }
        public float getDetectionConfidence() { return mDetectionConfidence; }
        public float getRecognitionConfidence() { return mRecognitionConfidence; }
        public float[] getWarpedBox() { return mWarpedBox; }
    }

    static public final long extractFrameId(final UltMicrSdkResult result) {
        final String jsonString = result.json();
        if (jsonString != null) {
            try {
                final JSONObject jObject = new JSONObject(jsonString);
                return jObject.getLong("frame_id");
            }
            catch (JSONException e) { }
        }
        return 0;
    }

    static public final List<Zone> extractZones(final UltMicrSdkResult result) {
        final List<Zone> zones = new LinkedList<>();
        if (!result.isOK() || result.numZones() == 0) {
            return zones;
        }
        final String jsonString = result.json();
        //final String jsonString = "{\"code\":200,\"duration\":332,\"phrase\":\"OK\",\"zones\":[{\"confidences\":[90.15003204345703,100],\"description\":\"CMC-7\",\"fields\":[{\"Bank\":\"001\"},{\"Agency\":\"3541\"},{\"DV2\":\"2\"},{\"Bank code\":\"016\"},{\"Check number\":\"280742\"},{\"Typification\":\"5\"},{\"DV1\":\"8\"},{\"Account\":\"1400108391\"},{\"DV3\":\"5\"}],\"klass\":2,\"skew\":-0.05488986259415382,\"text\":\"H00135412H0162807425I814001083915F\",\"warpedBox\":[71,446,484,446,484,487,71,487]},{\"confidences\":[90.15355682373047,100],\"description\":\"CMC-7\",\"fields\":[{\"Bank\":\"033\"},{\"Agency\":\"4533\"},{\"DV2\":\"9\"},{\"Bank code\":\"018\"},{\"Check number\":\"002154\"},{\"Typification\":\"5\"},{\"DV1\":\"7\"},{\"Account\":\"0101000055\"},{\"DV3\":\"0\"}],\"klass\":2,\"skew\":-0.04946456860159136,\"text\":\"H03345339H0180021545I701010000550F\",\"warpedBox\":[-1,188,422,188,422,232,-1,232]}]}";
        if (jsonString == null) { // No line
            return zones;
        }

        try {
            final JSONObject jObject = new JSONObject(jsonString);
            if (jObject.has("zones") && !jObject.isNull("zones")) {
                final JSONArray jZones = jObject.getJSONArray("zones");
                for (int i = 0; i < jZones.length(); ++i) {
                    final JSONObject jZone = jZones.getJSONObject(i);
                    final JSONArray jWarpedBox = jZone.getJSONArray("warpedBox");
                    final Zone zone = new Zone();
                    zone.mWarpedBox = new float[8];
                    for (int j = 0; j < 8; ++j) {
                        zone.mWarpedBox[j] = (float) jWarpedBox.getDouble(j);
                    }
                    final JSONArray jConfidences = jZone.getJSONArray("confidences"); // 1x2 array
                    zone.mRecognitionConfidence = (float) jConfidences.getDouble(0);
                    zone.mDetectionConfidence = (float) jConfidences.getDouble(1);
                    zone.mDescription = jZone.getString("description");
                    zone.mText = jZone.getString("text");
                    zone.mSkew = (float) jZone.getDouble("skew");
                    zone.mKlass = jZone.getInt("klass");

                    zones.add(zone);
                }
            }
        }
        catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
        return zones;
    }

    /**
     * Checks if the returned result is success. An assertion will be raised if it's not the case.
     * In production you should catch the exception and perform the appropriate action.
     * @param result The result to check
     * @return The same result
     */
    static public final UltMicrSdkResult assertIsOk(final UltMicrSdkResult result) {
        if (!result.isOK()) {
            throw new AssertionError("Operation failed: " + result.phrase());
        }
        return result;
    }

    /**
     * Converts the result to String.
     * @param result
     * @return
     */
    static public final String resultToString(final UltMicrSdkResult result) {
        return "code: " + result.code() + ", phrase: " + result.phrase() + ", numCards: " + result.numZones() + ", json: " + result.json();
    }

    /**
     *
     * @param fileName
     * @return Must close the returned object
     */
    static public FileChannel readFileFromAssets(final AssetManager assets, final String fileName) {
        FileInputStream inputStream = null;
        try {
            AssetFileDescriptor fileDescriptor = assets.openFd(fileName);
            inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            return inputStream.getChannel();
            // To return DirectByteBuffer: fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
            return null;
        }
    }
}