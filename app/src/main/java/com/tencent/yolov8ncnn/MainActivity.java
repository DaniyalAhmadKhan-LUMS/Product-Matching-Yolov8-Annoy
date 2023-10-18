package com.tencent.yolov8ncnn;
import android.content.Intent;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
import android.widget.Spinner;
import com.tencent.yolov8ncnn.databinding.MainBinding;

import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import android.view.TextureView;
import android.graphics.SurfaceTexture;
import android.view.Surface;

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
    private ImageView videoImage;
    private ZoomableImageView imageView;
    private Bitmap yourselectedImage = null;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private boolean isRtsp = false;
    private TextureView videoView;
    private Surface textureSurface;

    


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);

        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);
        videoView = findViewById(R.id.videoView);
        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                textureSurface = new Surface(surfaceTexture);
                mediaPlayer.getVLCVout().setVideoSurface(textureSurface, null);
                mediaPlayer.getVLCVout().attachViews();
                mediaPlayer.setAspectRatio("16:9");
                mediaPlayer.setScale(1);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
                mediaPlayer.getVLCVout().setWindowSize(i, i1);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                mediaPlayer.getVLCVout().detachViews();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                Bitmap currentFrameBitmap = videoView.getBitmap();
                processAndDisplayFrame(currentFrameBitmap);
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
    private void processAndDisplayFrame(Bitmap frame) {
        yolov8ncnn.detectImage(frame);
        // if(videoView.getVisibility() == View.VISIBLE) {
        //     videoView.setVisibility(View.INVISIBLE);
        // }
        // videoImage = (ImageView) findViewById(R.id.videoImageView);
        // videoImage.setImageBitmap(frame);
        // videoImage.setVisibility(View.VISIBLE);
        
    }
    private void initializePlayer() {
        final List<String> options = Arrays.asList("-vvv");
        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);
        
    }

    public void startRtspStream(View view) {
        if (isGallery) {
            imageView.setVisibility(View.GONE);
        } else {
            yolov8ncnn.closeCamera();
            cameraView.setVisibility(View.GONE);
        }
    
       playStream("rtsp://zephyr.rtsp.stream/movie?streamKey=688720e15d3da72fce9d21806f15d96e");  // replace with your RTSP link
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
        
        // Toggle view visibility
        imageView = (ZoomableImageView) findViewById(R.id.myImageView);
        imageView.setVisibility(View.GONE);
        cameraView.setVisibility(View.VISIBLE);

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
        if (textureSurface != null) {
            textureSurface.release();
        }
    }

}
