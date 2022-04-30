package com.camera.test;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.renderscript.RenderScript;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

import com.google.android.cameraview.CameraView;
import com.google.android.cameraview.CameraViewImpl;
import com.camera.test.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity  extends AppCompatActivity {

    private CameraView cameraView;
    private FrameLayout capturedLayout;
    private ImageView capturedImageView;
    private ImageView maskImageView;
    private ImageView backImageView;
    private ImageView changeCameraImageView;
    private ImageView savePhotoImageView;
    private ImageView takePhotoImageView;

    private static final int PERMISSION_CODE_STORAGE = 3001;
    private static final int PERMISSION_CODE_CAMERA = 3002;
    private boolean isCameraView = true;
    private RenderScript rs;
    private boolean frameIsProcessing = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        cameraView = findViewById(R.id.camera_view);
        capturedLayout = findViewById(R.id.fl_captured_image);
        capturedImageView = findViewById(R.id.iv_captured_image);
        maskImageView = findViewById(R.id.iv_mask);
        backImageView = findViewById(R.id.iv_back);
        changeCameraImageView = findViewById(R.id.iv_change_camera);
        savePhotoImageView = findViewById(R.id.iv_save_photo);
        takePhotoImageView = findViewById(R.id.iv_capture_photo);

        backImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCameraView == true) {
                    finish();
                } else {
                    isCameraView = true;
                    refreshUI();
                }
            }
        });

        changeCameraImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.switchCamera();
            }
        });

        AudioManager audioManager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        MediaActionSound sound = new MediaActionSound();

        takePhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sound.play(MediaActionSound.SHUTTER_CLICK);
                cameraView.takePicture();
            }
        });

        savePhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePhoto();
            }
        });

        isCameraView = true;
        refreshUI();

        rs = RenderScript.create(this);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String permissions[],
            @NonNull int grantResults[]) {
        switch (requestCode) {
            case PERMISSION_CODE_STORAGE:
            case PERMISSION_CODE_CAMERA:
                if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
        if (requestCode != PERMISSION_CODE_STORAGE && requestCode != PERMISSION_CODE_CAMERA) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissions();
    }

    @Override
    public void onPause() {
        cameraView.stop();
        super.onPause();
    }

    private void checkPermissions() {
        if (PermissionUtils.isStorageGranted(this) && PermissionUtils.isCameraGranted(this)) {
            cameraView.start();
            setupCameraCallbacks();
        } else {
            if (!PermissionUtils.isCameraGranted(this)) {
                PermissionUtils.checkPermission(this, Manifest.permission.CAMERA,
                        PERMISSION_CODE_CAMERA);
            } else {
                PermissionUtils.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        PERMISSION_CODE_STORAGE);
            }
        }
    }

    private void refreshUI() {
        if (isCameraView == true) {
            cameraView.setVisibility(View.VISIBLE);
            capturedImageView.setVisibility(View.GONE);
//            maskImageView.setVisibility(View.VISIBLE);
            changeCameraImageView.setVisibility(View.VISIBLE);
            savePhotoImageView.setVisibility(View.GONE);
            takePhotoImageView.setVisibility(View.VISIBLE);
        } else {
            cameraView.setVisibility(View.GONE);
            capturedImageView.setVisibility(View.VISIBLE);
//            maskImageView.setVisibility(View.GONE);
            changeCameraImageView.setVisibility(View.GONE);
            savePhotoImageView.setVisibility(View.VISIBLE);
            takePhotoImageView.setVisibility(View.GONE);
        }
    }

    private void setupCameraCallbacks() {
        cameraView.setOnPictureTakenListener(new CameraViewImpl.OnPictureTakenListener() {
            @Override
            public void onPictureTaken(Bitmap bitmap, int rotationDegrees) {
                Handler mainHandler = new Handler(getApplicationContext().getMainLooper());
                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Bitmap rotateBitmap = rotateImage(bitmap, rotationDegrees);
                        capturedImageView.setImageBitmap(rotateBitmap);
                        isCameraView = false;
                        refreshUI();
                    }
                };
                mainHandler.post(myRunnable);
            }
        });
        cameraView.setOnFocusLockedListener(new CameraViewImpl.OnFocusLockedListener() {
            @Override
            public void onFocusLocked() {
                playShutterAnimation();
            }
        });
        cameraView.setOnTurnCameraFailListener(new CameraViewImpl.OnTurnCameraFailListener() {
            @Override
            public void onTurnCameraFail(Exception e) {
                Toast.makeText(MainActivity.this, "Switch Camera Failed. Does you device has a front camera?",
                        Toast.LENGTH_SHORT).show();
            }
        });
        cameraView.setOnCameraErrorListener(new CameraViewImpl.OnCameraErrorListener() {
            @Override
            public void onCameraError(Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        cameraView.setOnFrameListener(new CameraViewImpl.OnFrameListener() {
            @Override
            public void onFrame(final byte[] data, final int width, final int height, int rotationDegrees) {
                if (frameIsProcessing) return;
                frameIsProcessing = true;
            }
        });
    }

    private void playShutterAnimation() {
    }

    private void notifyGallery(String filePath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(filePath);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        sendBroadcast(mediaScanIntent);
    }

    private Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
//        matrix.postRotate(-angle);
        matrix.postRotate(90);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    private void savePhoto() {
        capturedLayout.setDrawingCacheEnabled(true);
        capturedLayout.buildDrawingCache(true);
        Bitmap bitmap = Bitmap.createBitmap(capturedLayout.getDrawingCache());
        capturedLayout.setDrawingCacheEnabled(false);

//        String filePath = CapturePhotoUtils.insertImage(getContentResolver(), bitmap, "Buratoku", "");
//        notifyGallery(filePath);
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = timeStamp + ".jpg";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            } else {
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File file = new File(directory, "Camera/" + fileName);
                values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
            }

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(fileName + " was saved.");
            AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(e.getLocalizedMessage());
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }
}
