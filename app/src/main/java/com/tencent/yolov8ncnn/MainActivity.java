package com.tencent.yolov8ncnn;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import com.tencent.yolov8ncnn.databinding.MainBinding;

import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.widget.ImageView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.Surface;
import android.os.Handler;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements SurfaceHolder.Callback
{
    public static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_SELECT_IMAGE = 1001;
    public static final int REQUEST_STORAGE = 101;
    private Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();
    private int facing = 0;
    private boolean isGallery = false;
    private MainBinding binding;

    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private SurfaceView cameraView;
    private ZoomableImageView imageView;
    private Bitmap yourselectedImage = null;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private boolean isRtsp = false;
    private TextureView textureView;

    private Surface textureSurface;
    private boolean isStreaming = false;
    private EditText rtspLinkInput;
    private Button strtStrmB;
    private ImageView processedFrameView;
    private TextView originalStreamLabel;

    private TextView detectionStreamLabel;

    


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // final List<String> options = Arrays.asList("-vvv");
        // libVLC = new LibVLC(this,options);
        // setContentView(R.layout.main);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);

        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);
        rtspLinkInput =(EditText) findViewById(R.id.rtspUrl);
        strtStrmB = (Button) findViewById(R.id.startStream);
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
        processedFrameView = findViewById(R.id.processed_frame);
        processedFrameView.setVisibility(View.GONE);
        originalStreamLabel = findViewById(R.id.originalStreamLabel);
        detectionStreamLabel = findViewById(R.id.detectionStreamLabel);

        textureView = (TextureView) findViewById(R.id.textureView);
//        textureView.setVisibility(View.INVISIBLE);
        textureView.setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Setup your media player with this surface for video output
                textureSurface = new Surface(surface);
                mediaPlayer.getVLCVout().setVideoSurface(textureSurface, null);
                mediaPlayer.getVLCVout().attachViews();
                mediaPlayer.setAspectRatio("16:9");
                mediaPlayer.setScale(0);
//                mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL);
                // mediaPlayer.play();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mediaPlayer.stop();
                mediaPlayer.getVLCVout().detachViews();
                return true;
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                mediaPlayer.getVLCVout().setWindowSize(width, height);
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                if (isStreaming) {
                    // New frame available, capture it
                    Bitmap currentFrame = textureView.getBitmap();
                    if (currentFrame != null) {
                        // Ensure the bitmap is in the correct format (RGBA_8888)
                        Bitmap compatibleFrame = Bitmap.createBitmap(currentFrame.getWidth(), currentFrame.getHeight(), Bitmap.Config.ARGB_8888);

                        // Copy the original bitmap's pixels to the new bitmap
                        Canvas canvas = new Canvas(compatibleFrame);
                        Paint paint = new Paint();
                        canvas.drawBitmap(currentFrame, 0, 0, paint);

                        // Now you can use the compatibleFrame for detection
                        yolov8ncnn.detectStream(compatibleFrame);

                        isRtsp = true;

                        // Clean up the bitmaps when done
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                processedFrameView.setImageBitmap(compatibleFrame);
                            }
                        });
                        currentFrame.recycle();
//                        compatibleFrame.recycle();
                    }

                    // Do something with the frame, e.g., process or save it
                    // Keep in mind that this operation is expensive, and doing it for every frame
                    // will significantly consume memory and CPU.
                    // Consider adding a condition to limit the capture rate or resolution.
                }
            }
        });



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
        
        
        initializePlayer();
        reload();
    }
    private void initializePlayer() {
        final List<String> options = Arrays.asList("-vvv");
        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);
//        mediaPlayer.setScale(0);
        mediaPlayer.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event event) {
                switch (event.type) {
                    case MediaPlayer.Event.Playing:
                        // Triggered when the media player starts playing
                        isStreaming = true;
                        break;
                    case MediaPlayer.Event.EndReached:
                    case MediaPlayer.Event.EncounteredError:
                        isStreaming = false;
                        break;
                }
            }
        });


    }


    public void startRtspStream(View view) {
        if (isGallery) {
            imageView.setVisibility(View.GONE);
        } else {
            yolov8ncnn.closeCamera();
            cameraView.setVisibility(View.GONE);
        }
        stopRtspStream();
        rtspLinkInput.setVisibility(View.VISIBLE);
        strtStrmB.setVisibility(View.VISIBLE);
        processedFrameView.setVisibility(View.VISIBLE);
        originalStreamLabel.setVisibility(View.VISIBLE);
        detectionStreamLabel.setVisibility(View.VISIBLE);
    

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
        playStream(rtspUrl);  // replace with your RTSP link
        isRtsp = true;

    }
    private void playStream(String url) {
        final Media media = new Media(libVLC, Uri.parse(url));
        mediaPlayer.setMedia(media);
        media.release();
        mediaPlayer.play();
    }
    private void stopRtspStream() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();  // Stop the RTSP stream
        }
        isRtsp = false;
    }
    public void resumeCamera(View view) {
        // Restart the camera
        stopRtspStream(); 
        yolov8ncnn.openCamera(1);
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
        processedFrameView.setVisibility(View.GONE);
        // Toggle view visibility
        imageView = (ZoomableImageView) findViewById(R.id.myImageView);
        imageView.setVisibility(View.GONE);
        cameraView.setVisibility(View.VISIBLE);
        originalStreamLabel.setVisibility(View.GONE);
        detectionStreamLabel.setVisibility(View.GONE);

    }

    public void openGallery(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        } else {
            onImageSelectButtonClick(view);
        }
    }

    public void onImageSelectButtonClick(View view) {
        stopRtspStream();
        yolov8ncnn.closeCamera(); // close camera first
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
        cameraView.setVisibility(View.GONE); // hide camera view
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
        processedFrameView.setVisibility(View.GONE);
        originalStreamLabel.setVisibility(View.GONE);
        detectionStreamLabel.setVisibility(View.GONE);
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
    public void onResume() {
        super.onResume();
    
        if (isRtsp) {
            if (mediaPlayer != null) {
                mediaPlayer.play();
            }
        } else {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
            }
    
            yolov8ncnn.openCamera(1);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    
        if (isRtsp && mediaPlayer != null) {
            mediaPlayer.pause();
        } else {
            yolov8ncnn.closeCamera();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (libVLC != null) {
            libVLC.release();
        }
    }

}
