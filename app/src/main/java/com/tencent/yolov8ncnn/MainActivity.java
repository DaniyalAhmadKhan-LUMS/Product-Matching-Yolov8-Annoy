package com.tencent.yolov8ncnn;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.io.IOException;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Toast;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.EditText;
import android.widget.Button;

public class MainActivity extends Activity implements SurfaceHolder.Callback
{
    public static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_SELECT_IMAGE = 1001;
    public static final int REQUEST_STORAGE = 101;
    private Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();
    private int facing = 0;
    private boolean isGallery = false;
    private boolean isRtsp = false;

    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private SurfaceView cameraView;
    private ZoomableImageView imageView;
    private Bitmap yourselectedImage = null;

    private EditText rtspLinkInput;
    private Button strtStrmB;
    private SurfaceView videoSurfaceView;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);

        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);
        rtspLinkInput =(EditText) findViewById(R.id.rtspUrl);
        strtStrmB = (Button) findViewById(R.id.startStream);
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
        videoSurfaceView = (SurfaceView) findViewById(R.id.videoSurfaceView);
        videoSurfaceView.getHolder().addCallback(this);



        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_cpugpu)
                {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        reload();
    }
    

    public void resumeCamera(View view) {
        // Restart the camera
        yolov8ncnn.openCamera(1);
        videoSurfaceView.setVisibility(View.GONE);
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);

        // Toggle view visibility
        imageView = (ZoomableImageView) findViewById(R.id.myImageView);
        imageView.setVisibility(View.GONE);
        cameraView.setVisibility(View.VISIBLE);

    }

    public void startRtspStream(View view) {
        yolov8ncnn.closeCamera();
        cameraView.setVisibility(View.GONE);
        imageView = (ZoomableImageView) findViewById(R.id.myImageView);
        imageView.setVisibility(View.GONE);
        rtspLinkInput.setVisibility(View.VISIBLE);
        strtStrmB.setVisibility(View.VISIBLE);
        videoSurfaceView.setVisibility(View.VISIBLE);

    }

    public void onStartStreamClicked(View view) {
        // ... existing setup before playing the stream ...
        String rtspUrl = rtspLinkInput.getText().toString().trim();
        if (!rtspUrl.isEmpty()) {
            // If the URL is not empty, hide the camera and ImageView, then start the stream

            startStream(rtspUrl); // This method should start streaming from the provided URL.
        } else {
            // Handle the case where the RTSP URL is empty (e.g., show a Toast message).
            Toast.makeText(this, "Please enter a valid RTSP link.", Toast.LENGTH_SHORT).show();
        }

    }
    public void startStream(String rtspUrl) {
        isRtsp = true;
        SurfaceHolder holderVideo = videoSurfaceView.getHolder();
        if (holderVideo != null) {
            // Call the JNI function to start video playback in this surface
            yolov8ncnn.rtspStream(rtspUrl,holderVideo.getSurface());
        }
    }

    public void openGallery(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        } else {
            onImageSelectButtonClick(view);
        }
    }

    public void onImageSelectButtonClick(View view) {
        yolov8ncnn.closeCamera(); // close camera first
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
        cameraView.setVisibility(View.GONE); // hide camera view
        videoSurfaceView.setVisibility(View.GONE);
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImageUri), null, options);
                yolov8ncnn.detectImage(bitmap);

                imageView = (ZoomableImageView) findViewById(R.id.myImageView);
                
                imageView.setImageBitmap(bitmap);

                imageView.setVisibility(View.VISIBLE); // show image view

            } catch (IOException e) {
                Log.e("MainActivity", "Error processing selected image", e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onImageSelectButtonClick(null);
            }
        }
    }






    private void reload()
    {
        boolean ret_init = yolov8ncnn.loadModel(getAssets(), current_model, current_cpugpu);
        if (!ret_init)
        {
            Log.e("MainActivity", "yolov8ncnn loadModel failed");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        yolov8ncnn.setOutputWindow(holder.getSurface());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }

        yolov8ncnn.openCamera(1);
    }

    @Override
    public void onPause()
    {
        super.onPause();

        yolov8ncnn.closeCamera();
    }

}
