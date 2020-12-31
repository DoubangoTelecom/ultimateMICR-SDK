/*
 * Copyright (C) 2016-2020 Doubango AI <https://www.doubango.org>
 * License: For non-commercial use only
 * Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
 * WebSite: https://www.doubango.org/webapps/micr/
 */
package org.doubango.ultimateMICR.Benchmark;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import org.doubango.ultimateMicr.Sdk.ULTMICR_SDK_IMAGE_TYPE;
import org.doubango.ultimateMicr.Sdk.UltMicrSdkEngine;
import org.doubango.ultimateMicr.Sdk.UltMicrSdkResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MICRBenchmarkActivity extends AppCompatActivity {

    /**
     * TAG used for the debug logs.
     */
    static final String TAG = MICRBenchmarkActivity.class.toString();

    /**
     * Defines the debug level to output on the console. You should use "verbose" for diagnostic, "info" in development stage and "warn" on production.
     * JSON name: "debug_level"
     * Default: "info"
     * type: string
     * pattern: "verbose" | "info" | "warn" | "error" | "fatal"
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-level
     */
    static final String CONFIG_DEBUG_LEVEL = "info";

    /**
     * Whether to write the transformed input image to the disk. This could be useful for debugging.
     * JSON name: "debug_write_input_image_enabled"
     * Default: false
     * type: bool
     * pattern: true | false
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#debug-write-input-image-enabled
     */
    static final boolean CONFIG_DEBUG_WRITE_INPUT_IMAGE = false; // must be false unless you're debugging the code

    /**
     * Defines the maximum number of threads to use.
     * You should not change this value unless you know what you’re doing. Set to -1 to let the SDK choose the right value.
     * The right value the SDK will choose will likely be equal to the number of virtual cores.
     * For example, on an octa-core device the maximum number of threads will be 8.
     * JSON name: "num_threads"
     * Default: -1
     * type: int
     * pattern: [-inf, +inf]
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#num-threads
     */
    static final int CONFIG_NUM_THREADS = -1;

    /**
     * Whether to enable GPGPU computing. This will enable or disable GPGPU computing on the computer vision and deep learning libraries.
     * On ARM devices this flag will be ignored when fixed-point (integer) math implementation exist for a well-defined function.
     * For example, this function will be disabled for the bilinear scaling as we have a fixed-point SIMD accelerated implementation.
     * Same for many deep learning parts as we’re using QINT8 quantized inference.
     * JSON name: "gpgpu_enabled"
     * Default: true
     * type: bool
     * pattern: true | false
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#gpgpu-enabled
     */
    static final boolean CONFIG_GPGPU_ENABLED = true;

    /**
     * A device contains a CPU and a GPU. Both can be used for math operations.
     * This option allows using both units. On some devices the CPU is faster and on other it's slower.
     * When the application starts, the work (math operations to perform) is equally divided: 50% for the CPU and 50% for the GPU.
     * Our code contains a profiler to determine which unit is faster and how fast (percentage) it is. The profiler will change how
     * the work is divided based on the time each unit takes to complete. This is why this configuration entry is named "workload balancing".
     * JSON name: "gpgpu_workload_balancing_enabled"
     * Default: false for x86 and true for ARM
     * type: bool
     * pattern: true | false
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#gpgpu-workload-balancing-enabled
     */
    static final boolean CONFIG_GPGPU_WORKLOAD_BALANCING_ENABLED = (System.getProperty("os.arch").equals("armv71") || System.getProperty("os.arch").equals("aarch64"));

    /**
     * Before calling the classifier to determine whether a zone contains a MICR line we need to segment the text using multi-layer segmenter followed by clustering.
     * The multi-layer segmenter uses hysteresis for the voting process using a [min, max] double thresholding values. This configuration entry defines how low the
     * thresholding values should be. Lower the values are, higher the number of fragments will be and higher the recall will be. High number of fragments means more
     * data to process which means more CPU usage and higher processing time.
     * JSON name: "segmenter_accuracy"
     * Default: high
     * type: string
     * pattern: "veryhigh" | "high" | "medium" | "low" | "verylow"
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#segmenter-accuracy
     */
    static final String CONFIG_SEGMENTER_ACCURACY = "high";

    /**
     * Defines the interpolation method to use when pixels are scaled, deskewed or deslanted. bicubic offers the best quality but is slow as there
     * is no SIMD or GPU acceleration yet. bilinear and nearest interpolations are multithreaded and SIMD accelerated. For most scenarios bilinear
     * interpolation is good enough to provide high accuracy/precision results while the code still runs very fast.
     * JSON name: "interpolation"
     * Default: bilinear
     * type: string
     * pattern: "nearest" | "bilinear" | "bicubic"
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#interpolation
     */
    static final String CONFIG_INTERPOLATION = "bilinear";

    /**
     * Defines the MICR format to enable for the detection. Use "e13b" to look for E-13B lines only and "cmc7" for CMC-7 lines only. To look for both, use "e13b+cmc7".
     * For performance reasons you should not use  "e13b+cmc7" unless you really expect the document to contain both E-13B and CMC7 lines.
     * JSON name: "interpolation"
     * Default: "e13b+cmc7"
     * type: string
     * pattern: "e13b" | "cmc7" | "e13b+cmc7"
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#format
     */
    static final String CONFIG_FORMAT = "e13b";

    /**
     * Define a threshold for the overall recognition score. Any recognition with a score below that threshold will be ignored.
     * The overall score is computed based on "score_type". 0.f being poor confidence and 1.f excellent confidence.
     * JSON name: "min_score"
     * Default: 0.3f
     * type: float
     * pattern: ]0.f, 1.f]
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#min-score
     */
    static final double CONFIG_MIN_SCORE = 0.3; // 30%

    /**
     * Defines the overall score type. The recognizer outputs a recognition score ([0.f, 1.f]) for every character in the license plate.
     * The score type defines how to compute the overall score.
     * - "min": Takes the minimum score.
     * - "mean": Takes the average score.
     * - "median": Takes the median score.
     * - "max": Takes the maximum score.
     * - "minmax": Takes (max + min) * 0.5f.
     * The "min" score is the more robust type as it ensure that every character have at least a certain confidence value.
     * The median score is the default type as it provide a higher recall. In production we recommend using min type.
     * JSON name: "score_type"
     * Default: "median"
     * Recommended: "min"
     * type: string
     *  More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#score-type
     */
    static final String CONFIG_SCORE_TYPE = "min";

    /**
     * Defines the Region Of Interest (ROI) for the detector. Any pixels outside region of interest will be ignored by the detector.
     * Defining an WxH region of interest instead of resizing the image at WxH is very important as you'll keep the same quality when you define a ROI while you'll lose in quality when using the later.
     * JSON name: "roi"
     * Default: [0.f, 0.f, 0.f, 0.f]
     * type: float[4]
     * pattern: [left, right, top, bottom]
     * More info: https://www.doubango.org/SDKs/micr/docs/Configuration_options.html#roi
     */
    static final List<Float> CONFIG_ROI = Arrays.asList(0.f, 0.f, 0.f, 0.f);


    /**
     * Number of times to try.
     * This number must be high enough (> 50) to make sure the noise is relatively small.
     */
    static final int NUM_LOOPS = 100;

    /**
     * The percentage of images with at least #1 MICR line. Within [0, 1] interval.
     */
    static final float PERCENT_POSITIVES = .2f; // 20%

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_micrbenchmark);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Check some values
        if (PERCENT_POSITIVES < 0 || PERCENT_POSITIVES > 1) {
            throw new AssertionError("PERCENT_POSITIVES must be within [0, 1]");
        }
        if (NUM_LOOPS <= 0) {
            throw new AssertionError("NUM_LOOPS must be > 0");
        }

        // Initialize the engine
        UltMicrSdkResult result = assertIsOk(UltMicrSdkEngine.init(
                getAssets(),
                getConfig()
        ));
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();

        final TextView textView = findViewById(R.id.textView);
        textView.setText("*** Preparing... ***");

        // Create image indices
        List<Integer> indices = new ArrayList<>(NUM_LOOPS);
        final int numPositives = (int)(NUM_LOOPS * PERCENT_POSITIVES);
        for (int i = 0; i < numPositives; ++i) {
            indices.add(1); // positive index
        }
        for (int i = numPositives; i < NUM_LOOPS; ++i) {
            indices.add(0); // negative index
        }
        Collections.shuffle(indices); // make the indices random

        // Read the images
        final MICRImage images[] = new MICRImage[2];
        images[0] = readFile("traffic_1280x720.jpg");
        if (images[0] == null) {
            throw new AssertionError("Failed to read file");
        }
        images[1] = readFile("e13b_1280x720.jpg"); // Testing E-13B format. For CMC-7 or both please change CONFIG_FORMAT
        if (images[1] == null) {
            throw new AssertionError("Failed to read file");
        }

        // Warm up to prepare for benchmark
        assertIsOk(UltMicrSdkEngine.warmUp(images[1].mType));

        textView.setText("*** Started timing... ***");

        // Processing
        Log.i(TAG, "*** Started timing... ***");
        final long startTimeInMillis = SystemClock.uptimeMillis();
        for (Integer i : indices) {
            final MICRImage image = images[i];
            assertIsOk(UltMicrSdkEngine.process(
                    image.mType,
                    image.mBuffer,
                    image.mWidth,
                    image.mHeight
            ));
        }
        final long endTimeInMillis = SystemClock.uptimeMillis();
        final long elapsedTime = (endTimeInMillis - startTimeInMillis);
        final float estimatedFps = 1000.f / (elapsedTime / (float)NUM_LOOPS);

        Log.i(TAG, "Elapsed time: " + elapsedTime + " millis, FrameRate: " + estimatedFps);

        textView.setText("Elapsed time: " + (endTimeInMillis - startTimeInMillis) + " millis" + ", Frame rate: " + estimatedFps);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        final UltMicrSdkResult result = assertIsOk(UltMicrSdkEngine.deInit());

        super.onDestroy();
    }

    MICRImage readFile(final String name) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeStream(getAssets().open(name), null, options);
        }
        catch(IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
            return null;
        }
        if (bitmap.getRowBytes() < bitmap.getWidth() << 2) {
            throw new AssertionError("Not ARGB");
        }

        final int widthInBytes = bitmap.getRowBytes();
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final ByteBuffer nativeBuffer = ByteBuffer.allocateDirect(widthInBytes * height);
        bitmap.copyPixelsToBuffer(nativeBuffer);
        nativeBuffer.rewind();

        return new MICRImage(ULTMICR_SDK_IMAGE_TYPE.ULTMICR_SDK_IMAGE_TYPE_RGBA32, nativeBuffer, width, height);
    }

    final String getConfig() {
        // More information on the JSON config at https://www.doubango.org/SDKs/micr/docs/Configuration_options.html
        JSONObject config = new JSONObject();
        try {
            config.put("debug_level", CONFIG_DEBUG_LEVEL);
            config.put("debug_write_input_image_enabled", CONFIG_DEBUG_WRITE_INPUT_IMAGE);
            if (CONFIG_DEBUG_WRITE_INPUT_IMAGE) {
                // Create folder to dump input images for debugging
                File dummyFile = new File(getExternalFilesDir(null), "dummyFile");
                if (!dummyFile.getParentFile().exists() && !dummyFile.getParentFile().mkdirs()) {
                    Log.e(TAG, "mkdir failed: " + dummyFile.getParentFile().getAbsolutePath());
                }
                final String debugInternalDataPath = dummyFile.getParentFile().exists() ? dummyFile.getParent() : Environment.getExternalStorageDirectory().getAbsolutePath();
                dummyFile.delete();
                config.put("debug_internal_data_path", debugInternalDataPath);
            }

            config.put("num_threads", CONFIG_NUM_THREADS);
            config.put("gpgpu_enabled", CONFIG_GPGPU_ENABLED);
            config.put("gpgpu_workload_balancing_enabled", CONFIG_GPGPU_WORKLOAD_BALANCING_ENABLED);

            config.put("segmenter_accuracy", CONFIG_SEGMENTER_ACCURACY);
            config.put("interpolation", CONFIG_INTERPOLATION);
            config.put("format", CONFIG_FORMAT);
            config.put("roi", new JSONArray(CONFIG_ROI));
            config.put("score_type", CONFIG_SCORE_TYPE);
            config.put("min_score", CONFIG_MIN_SCORE);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        return config.toString();
    }

    static class MICRImage {
        final ULTMICR_SDK_IMAGE_TYPE mType;
        final ByteBuffer mBuffer;
        final int mWidth;
        final int mHeight;

        MICRImage(final ULTMICR_SDK_IMAGE_TYPE type, final ByteBuffer buffer, final int width, final int height) {
            mType = type;
            mBuffer = buffer;
            mWidth = width;
            mHeight = height;
        }
    }

    /**
     * Checks if the result is success. Raise an exception if the result is failure.
     * @param result the result to check
     * @return the same result received in param.
     */
    static final UltMicrSdkResult assertIsOk(final UltMicrSdkResult result) {
        if (!result.isOK()) {
            throw new AssertionError("Operation failed: " + result.phrase());
        }
        return result;
    }

    /**
     * Converts the result to String for display.
     * @param result the result to convert
     * @return the String representing the result
     */
    static final String resultToString(final UltMicrSdkResult result) {
        return "code: " + result.code() + ", phrase: " + result.phrase() + ", numCards: " + result.numZones() + ", json: " + result.json();
    }


}
