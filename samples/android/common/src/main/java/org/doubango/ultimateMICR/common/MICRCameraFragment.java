/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright (C) 2016-2020 Doubango AI <https://www.doubango.org>
 * License: For non-commercial use only
 * Source code: https://github.com/DoubangoTelecom/ultimateMICR-SDK
 * WebSite: https://www.doubango.org/webapps/micr/
 */

package org.doubango.ultimateMICR.common;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.doubango.ultimateMICR.VideoRecognizer.R; // FIXME(dmi): must remove


public class MICRCameraFragment extends Fragment
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    static final int REQUEST_CAMERA_PERMISSION = 1;

    static final String FRAGMENT_DIALOG = "dialog";

    static final String TAG = MICRCameraFragment.class.getCanonicalName();

    static final int VIDEO_FORMAT = ImageFormat.YUV_420_888; // All Android devices are required to support this format

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Using #2: processing and pending.
     */
    static final int MAX_IMAGES = 3;

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of
     * containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    static final int MINIMUM_PREVIEW_SIZE = 320;

    private Size mPreferredSize = null;

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    private int mJpegOrientation = 1;

    /**
     * An {@link MICRGLSurfaceView} for camera preview.
     */
    private MICRGLSurfaceView mGLSurfaceView;

    private MICRView mMICRView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    private CCardCameraFragmentSink mSink;

    private final MICRBackgroundTask mBackgroundTaskCamera = new MICRBackgroundTask();
    private final MICRBackgroundTask mBackgroundTaskDrawing = new MICRBackgroundTask();
    private final MICRBackgroundTask mBackgroundTaskInference = new MICRBackgroundTask();

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    private boolean mClosingCamera = false;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReaderInference;

    private ImageReader mImageReaderDrawing;


    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (mClosingCamera) {
                Log.d(TAG, "Closing camera");
                return;
            }
            try {
                final Image image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }

                final boolean isForDrawing = (reader.getSurface() == mImageReaderDrawing.getSurface());
                if (isForDrawing) {
                    /*mBackgroundTaskDrawing.post(() ->*/ mGLSurfaceView.setImage(image, mJpegOrientation)/*)*/;
                }
                else {
                    /*mBackgroundTaskInference.post(() ->*/ mSink.setImage(image, mJpegOrientation)/*)*/;
                }

            } catch (final Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
    };

    private CaptureRequest.Builder mCaptureRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mCaptureRequestBuilder}
     */
    private CaptureRequest mCaptureRequest;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * Default constructor automatically called when the fragment is recreated. Required.
     * https://stackoverflow.com/questions/51831053/could-not-find-fragment-constructor
     */
    public MICRCameraFragment() {
        // nothing special here
    }

    private MICRCameraFragment(final Size preferredSize, final CCardCameraFragmentSink sink) {
        mPreferredSize = preferredSize;
        mSink = sink;
    }

    /**
     * Public function to be called to create the fragment.
     * @param preferredSize
     * @return
     */
    public static MICRCameraFragment newInstance(final Size preferredSize, final CCardCameraFragmentSink sink) {
        return new MICRCameraFragment(preferredSize, sink);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mGLSurfaceView = (MICRGLSurfaceView) view.findViewById(R.id.glSurfaceView);
        mMICRView = (MICRView) view.findViewById(R.id.micrView);
        //mMICRView.setBackgroundColor(Color.RED);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        startBackgroundThreads();

        // Forward the plateView to the sink
        if (mSink != null && mMICRView != null) {
            mSink.setMICRView(mMICRView);
        }

        // Open the camera
        openCamera(mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
    }

    @Override
    public synchronized void onPause() {
        closeCamera();
        stopBackgroundThreads();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param width The minimum desired width
     * @param height The minimum desired height
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
        final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
        final Size desiredSize = new Size(width, height);

        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        Log.i(TAG, "Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
        Log.i(TAG, "Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
        Log.i(TAG, "Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

        if (exactSizeFound) {
            Log.i(TAG, "Exact size match found.");
            return desiredSize;
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            Log.i(TAG, "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs() {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                // JPEG orientation
                // https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#JPEG_ORIENTATION
                int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                mJpegOrientation = (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize =
                        chooseOptimalSize(
                                map.getOutputSizes(SurfaceTexture.class),
                                mPreferredSize.getWidth(),
                                mPreferredSize.getHeight());

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                final int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mGLSurfaceView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    mMICRView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mGLSurfaceView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    mMICRView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link MICRCameraFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs();
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundTaskCamera.getHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mClosingCamera = true;
            mCameraOpenCloseLock.acquire();

            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReaderInference) {
                mImageReaderInference.close();
                mImageReaderInference = null;
            }
            if (null != mImageReaderDrawing) {
                mImageReaderDrawing.close();
                mImageReaderDrawing = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            mClosingCamera = false;
        }
    }

    /**
     * Starts a background threads
     */
    private void startBackgroundThreads() {
        mBackgroundTaskInference.start("InferenceBackgroundThread");
        mBackgroundTaskDrawing.start("DrawingBackgroundThread");
        mBackgroundTaskCamera.start("CameraBackgroundThread");
    }

    /**
     * Stops the background threads
     */
    private void stopBackgroundThreads() {
        mBackgroundTaskInference.stop();
        mBackgroundTaskDrawing.stop();
        mBackgroundTaskCamera.stop();
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraCaptureSession() {
        try {
            // Create Image readers
            mImageReaderInference = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    VIDEO_FORMAT, MAX_IMAGES);
            mImageReaderInference.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundTaskCamera.getHandler());

            mImageReaderDrawing = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    VIDEO_FORMAT, MAX_IMAGES);
            mImageReaderDrawing.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundTaskCamera.getHandler());

            // We set up a CaptureRequest.Builder with the output Surface to the image reader
            mCaptureRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(1, 25));
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE,
            //        CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
            //        CaptureRequest.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO);
            mCaptureRequestBuilder.addTarget(mImageReaderInference.getSurface());
            mCaptureRequestBuilder.addTarget(mImageReaderDrawing.getSurface());

            // Here, we create a CameraCaptureSession
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReaderInference.getSurface(), mImageReaderDrawing.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous
                                mCaptureRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                mCaptureRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start grabbing the frames
                                mCaptureRequest = mCaptureRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mCaptureRequest,
                                        null, mBackgroundTaskCamera.getHandler());

                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, mBackgroundTaskCamera.getHandler()
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    public static interface CCardCameraFragmentSink {

        /**
         *
         * @param view
         */
        public void setMICRView(@NonNull final MICRView view);

        /**
         *
         * @param image
         * @param jpegOrientation
         */
        public void setImage(@NonNull final Image image, final int jpegOrientation);
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

}