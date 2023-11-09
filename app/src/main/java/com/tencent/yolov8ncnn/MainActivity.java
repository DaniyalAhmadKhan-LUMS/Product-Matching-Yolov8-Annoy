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

import com.google.android.material.navigation.NavigationView;
import com.tencent.yolov8ncnn.databinding.MainBinding;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

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
import android.widget.LinearLayout;
import android.view.Gravity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.widget.SeekBar;
import java.util.Locale;
import android.text.Editable;
import android.text.TextWatcher;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, NavigationView.OnNavigationItemSelectedListener
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
    private Button stopStrmB;
    private ImageView processedFrameView;
    private TextView originalStreamLabel;

    private TextView detectionStreamLabel;

    private List<DetectedObject> objectDect;
    private volatile boolean cameraActive = false;
    private Thread cameraThread;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;
    private float globalValue;
    private String globalVariable;

    private boolean hasWebhookPushFailed = false;
    private String previousWebhookUrl = null;






    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Button confirmSettingsButton = findViewById(R.id.confirm_settings_button);
        confirmSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Apply changed settings
                applySettings();

                // Refresh the app (assuming 'reload()' does this)
//                reload();
            }
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();



        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
        // Set the navigation view navigation item selected listener
        navigationView.setNavigationItemSelectedListener(this);

        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_slider);
        View actionView = menuItem.getActionView();
        if (actionView != null) {
            SeekBar seekBar = actionView.findViewById(R.id.slider);
            final TextView textView = actionView.findViewById(R.id.slider_value);
            if (seekBar != null) {
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                        globalValue = progress / 100f; // Since SeekBar only supports integer, we divide by 100 to get a float value.
                        // You can now use globalValue anywhere in your Activity.
                        textView.setText(String.format(Locale.getDefault(), "%.2f", progress / 100f));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // Handle touch start if needed
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // Handle touch stop if needed
                    }
                });
            }
        }
        MenuItem editItem = navigationView.getMenu().findItem(R.id.nav_edit_text);
        View editView = editItem.getActionView();
        EditText editText = editView.findViewById(R.id.sidebar_edit_text);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing needed here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Assign the text to your global variable
//                globalVariable = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Maybe do some validation here
            }
        });



        cameraView = (SurfaceView) findViewById(R.id.cameraview);
        cameraView.setZOrderMediaOverlay(true);
        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);
        rtspLinkInput =(EditText) findViewById(R.id.rtspUrl);
        strtStrmB = (Button) findViewById(R.id.startStream);

        stopStrmB = (Button) findViewById(R.id.stopStream);
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
        stopStrmB.setVisibility(View.GONE);
        processedFrameView = findViewById(R.id.processed_frame);
        processedFrameView.setVisibility(View.GONE);
        originalStreamLabel = findViewById(R.id.originalStreamLabel);
        detectionStreamLabel = findViewById(R.id.detectionStreamLabel);

        textureView = (TextureView) findViewById(R.id.textureView);



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
    private void applySettings() {
        // Get values from SeekBar and EditText and assign to global variables
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_slider);
        View actionViewSlider = menuItem.getActionView();
        SeekBar seekBar = actionViewSlider.findViewById(R.id.slider);
        globalValue = seekBar.getProgress() / 100f; // Assuming globalValue is a float

        menuItem = navigationView.getMenu().findItem(R.id.nav_edit_text);
        View actionViewEditText = menuItem.getActionView();
        EditText editText = actionViewEditText.findViewById(R.id.sidebar_edit_text);
        globalVariable = editText.getText().toString(); // Assuming globalVariable is a String
    }
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        toggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns true,
        // then it has handled the app icon touch event
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle other action bar items...
        return super.onOptionsItemSelected(item);
    }
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation drawer item clicks here
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_settings:
                // Handle settings action
                break;
            // Handle other menu items if any
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        // Close drawer if open
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void initializePlayer() {
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-audio-time-stretch");
//        options.add("--vout=android-display");
        options.add("-vvv");
        options.add("--no-sub-autodetect-file");
        options.add("--swscale-mode=0");
        options.add("--network-caching=10000");
        options.add("--avcodec-hw=any");
        options.add("--rtsp-tcp");
        options.add("--http-continuous");
        options.add("--repeat");
        options.add("--loop");
        options.add("-R");
        options.add("--http-reconnect");





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
            deactivateCameraT();
            yolov8ncnn.closeCamera();
            cameraView.setVisibility(View.GONE);
        }
//        stopRtspStream();
        rtspLinkInput.setVisibility(View.VISIBLE);
        strtStrmB.setVisibility(View.VISIBLE);
        stopStrmB.setVisibility(View.VISIBLE);
        processedFrameView.setVisibility(View.VISIBLE);
        originalStreamLabel.setVisibility(View.VISIBLE);
        detectionStreamLabel.setVisibility(View.VISIBLE);


    }
    public void onStartStreamClicked(View view) {

        String rtspUrl = rtspLinkInput.getText().toString().trim();
        if (!rtspUrl.isEmpty()) {


            startStream(rtspUrl);
        } else {

            Toast.makeText(this, "Please enter a valid RTSP link.", Toast.LENGTH_SHORT).show();
        }

    }
    public void startStream(String rtspUrl) {
        if(mediaPlayer.getVLCVout().areViewsAttached())
        {
            mediaPlayer.getVLCVout().detachViews();
        }
        if (mediaPlayer == null) {
            initializePlayer();

        }

        playStream(rtspUrl);
        isRtsp = true;


    }
    private void playStream(String url) {
        final Media media = new Media(libVLC, Uri.parse(url));
        media.parse(Media.Parse.FetchNetwork);

        media.setEventListener(new Media.EventListener() {
            @Override
            public void onEvent(Media.Event event) {
                if (event.type == Media.Event.ParsedChanged) {
                    updateAspectRatio(media);  // see the method below
                }
            }
        });
        mediaPlayer.setMedia(media);
        media.release();
        if (textureView.isAvailable()) {
            setupMediaPlayerSurface();
        }
        mediaPlayer.play();
    }

    private void updateAspectRatio(Media media) {
        // Get the tracks information.
        for (Media.Track track : media.getTracks()) {
            if (track.type == Media.Track.Type.Video) {
                // We've found the video track. Now, let's get its dimensions.
                Media.VideoTrack videoTrack = (Media.VideoTrack) track;
                int videoWidth = videoTrack.width;
                int videoHeight = videoTrack.height;

                String aspectRatio = videoWidth + ":" + videoHeight;
                mediaPlayer.setAspectRatio(null);
                mediaPlayer.setScale(0);  // You can adjust scaling based on your requirement.
                break;
            }
        }
    }

    private void setupMediaPlayerSurface() {
        if (textureSurface == null && textureView.isAvailable()) {
            // Create a surface for the media player if it's null or old one was destroyed
            textureSurface = new Surface(textureView.getSurfaceTexture());

        }
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        mediaPlayer.getVLCVout().setVideoSurface(textureSurface, null);
        mediaPlayer.getVLCVout().setWindowSize(textureView.getWidth(), textureView.getHeight());
        mediaPlayer.getVLCVout().attachViews();
    }

    private void stopRtspStream() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.getVLCVout().detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        rtspLinkInput.setText("");
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);

        }
        processedFrameView.setImageBitmap(null);
        isRtsp = false;
        isStreaming = false;

        Toast.makeText(this, "Stream stopped and cleared.", Toast.LENGTH_SHORT).show();

    }
    public void onStopStreamClicked(View view){
        stopRtspStream();
    }

    private void startCameraThread() {
        cameraThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (cameraActive) {
                    // Fetch the YOLO output
                    objectDect = yolov8ncnn.getCameraYOLOout();
                    if (objectDect!=null){
                        pushToWebhook(objectDect);
                    }

                    // Process the YOLO output as needed
                    // ...

                    // For example, update the UI with the YOLO output on the main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Update your UI with the YOLO output
                            // ...
                        }
                    });

                    // Sleep a bit to prevent tight looping (optional but recommended)
                    try {
                        Thread.sleep(100); // Sleep for 100 milliseconds
                    } catch (InterruptedException e) {
                        // Handle the interruption
                        Thread.currentThread().interrupt(); // Set the interrupt flag again
                        break; // Break out of the loop if the thread is interrupted
                    }
                }
            }
        });
        cameraThread.start();
    }
    private void pushToWebhook(List<DetectedObject> detectedObjects) {
        // You need to replace this with your actual webhook URL

        if (globalVariable == null) {
            Log.e("WebhookError", "The webhook URL (globalVariable) is null.");
            showToastOnce("Webhook URL has not been set.");
            return; // Exit the method if globalVariable is null
        }
        String webhookUrl = globalVariable;
        if (previousWebhookUrl == null || !webhookUrl.equals(previousWebhookUrl)) {
            hasWebhookPushFailed = false; // Reset the flag if the URL has changed.
            previousWebhookUrl = webhookUrl; // Update the previous URL to the current one.
        }
        HttpURLConnection connection = null;

        try {
            URL url = new URL(webhookUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Prepare the payload
            JSONObject payload = new JSONObject();
            JSONArray data = new JSONArray();

            for (DetectedObject object : detectedObjects) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("productName", object.labelName);
                jsonObject.put("confidence", object.prob);
                jsonObject.put("item_area", object.bboxArea);
                jsonObject.put("item_width", object.width);
                jsonObject.put("item_height",object.height);
                long timestamp = System.currentTimeMillis();
                jsonObject.put("timestamp",timestamp);
                // Add other details as needed
                data.put(jsonObject);
            }

            payload.put("data", data);
            payload.put("success", true);

            // Convert JSONObject to JSON string and send as the request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Read the response from the server
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                Log.d("WebhookResponse", response.toString());
            }

        } catch (Exception e) {
            Log.e("WebhookError", "Error sending to webhook", e);
            if (!hasWebhookPushFailed) {
                hasWebhookPushFailed = true; // Set the flag so we don't show the toast multiple times.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Unable to push the data to webhook, the URL might be incorrect", Toast.LENGTH_LONG).show();
                    }
                });
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    private void showToastOnce(String message) {
        if (!hasWebhookPushFailed) {
            hasWebhookPushFailed = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void activateCameraT() {
        cameraActive = true;
        startCameraThread();
    }

    // Call this method when you want to stop fetching YOLO output
    private void deactivateCameraT() {
        cameraActive = false;
        if (cameraThread != null) {
            cameraThread.interrupt();
            try {
                cameraThread.join(); // Wait for the thread to finish
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt(); // Restore interrupt flag
            }
        }
    }

    public void resumeCamera(View view) {
        // Restart the camera
        stopRtspStream();
        yolov8ncnn.openCamera(1);
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
        stopStrmB.setVisibility(View.GONE);
        processedFrameView.setVisibility(View.GONE);
        // Toggle view visibility
        imageView = (ZoomableImageView) findViewById(R.id.myImageView);
        imageView.setVisibility(View.GONE);
        cameraView.setVisibility(View.VISIBLE);
        originalStreamLabel.setVisibility(View.GONE);
        detectionStreamLabel.setVisibility(View.GONE);
        activateCameraT();

    }

    public void openGallery(View view) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        } else {
            onImageSelectButtonClick(view);
        }
    }

    public void onImageSelectButtonClick(View view) {
        stopRtspStream();
        deactivateCameraT();
        yolov8ncnn.closeCamera(); // close camera first
        cameraView.setVisibility(View.GONE);
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
        // hide camera view
        rtspLinkInput.setVisibility(View.GONE);
        strtStrmB.setVisibility(View.GONE);
        stopStrmB.setVisibility(View.GONE);
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
//        objectDect = yolov8ncnn.getCameraYOLOout();
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
            // fix this!!!
            if (mediaPlayer != null) {
                mediaPlayer.play();
            }
        } else {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
            }
            activateCameraT();
            yolov8ncnn.openCamera(1);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (isRtsp && mediaPlayer != null) {
            mediaPlayer.pause();
        } else {
            deactivateCameraT();
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
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {


        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            mediaPlayer.getVLCVout().setWindowSize(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {

            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.getVLCVout().detachViews();
            }
            textureSurface = null;
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            if (isStreaming) {

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
//                            processedFrameView.setVisibility(View.VISIBLE);
                        }
                    });
                    currentFrame.recycle();
//                        compatibleFrame.recycle();
                }


            }
        }
    };
}
