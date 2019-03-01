package d.org.boom;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.FileObserver;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageButton;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.github.rahatarmanahmed.cpv.CircularProgressView;

import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    AppCompatImageButton record;

    private MediaRecorder recorder;
    private SurfaceHolder holder;
    private CamcorderProfile camcorderProfile;
    private Camera camera;
    int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    int orientation = 0;
    long start = System.currentTimeMillis();
    boolean recording = false,recordingState=false;
    boolean usecamera = true;
    boolean previewRunning = false;
    List<String> clips = new ArrayList<String>();
    File mVideoOutputFile=null;
    String clip="";
    boolean boomerang=true;
    public static FileObserver fileObserver;
    int maxTimeRecord = 2100;
    CountDownTimer timer=null;
    TextView counter;
    SurfaceView cameraView;
    CircularProgressView progressView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        record = (AppCompatImageButton)findViewById(R.id.record_button);
        counter = (TextView)findViewById(R.id.counter);
        cameraView = (SurfaceView)findViewById(R.id.CameraView);
        progressView = (CircularProgressView)findViewById(R.id.progress_bar);

        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCameraTask();
            }
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }

        timer = new CountDownTimer(maxTimeRecord,50) {
            @Override
            public void onTick(long l) {
                double sec = (double)(System.currentTimeMillis()-start)/1000.0;
                if (sec/1000<maxTimeRecord){
                    try {
                        counter.setText("00:" + String.format("%06.3f",sec));
                    }catch (Exception e){
                    }
                }
            }

            @Override
            public void onFinish() {
                counter.setText("00:" + String.format("%06.3f",(double)maxTimeRecord/1000.0));
            }
        };

        camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        /*if (CamcorderProfile.hasProfile(currentCameraId,CamcorderProfile.QUALITY_1080P)) {
            camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        }else */if (CamcorderProfile.hasProfile(currentCameraId,CamcorderProfile.QUALITY_720P)) {
            camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        }else if (CamcorderProfile.hasProfile(currentCameraId,CamcorderProfile.QUALITY_480P)) {
            camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        }

        camcorderProfile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
        camcorderProfile.videoCodec = MediaRecorder.VideoEncoder.H264;
        camcorderProfile.videoFrameRate = 30;

        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //openCameraTask();
    }

    public void openCameraTask(){
        new AsyncTask<Void, Void, Void>() {
            protected void onPreExecute() {
                progressView.setVisibility(View.VISIBLE);
            }
            protected Void doInBackground(Void... unused) {
                openCamera();
                prepareRecorder();
                return null;
            }
            protected void onPostExecute(Void unused) {
                counter.setVisibility(View.VISIBLE);
                recorder.start();
                timer.start();
            }
        }.execute();
    }

    public void openCamera(){
        if (usecamera) {

            camera = Camera.open(currentCameraId);
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                orientation = getCorrectCameraOrientation(info,camera);
                Camera.Parameters p = camera.getParameters();
                p.setRotation(orientation);
                camera.setParameters(p);
                camera.setDisplayOrientation(orientation);
            }

            try {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
                previewRunning = true;
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String makeUriString(String ext) {
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        String fileName = System.nanoTime()+"."+ext;
        return  root+"/"+fileName;
    }

    public void prepareRecorder() {
        //
        counter.setVisibility(View.GONE);
        recorder = new MediaRecorder();
        recorder.setPreviewDisplay(holder.getSurface());

        if (usecamera) {
            camera.unlock();
            recorder.setCamera(camera);
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        recorder.setProfile(camcorderProfile);


        if (fileObserver!=null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }
        // This is all very sloppy
        clip = makeUriString("mp4");
        start = 0;
        recordingState = false;

        fileObserver = new FileObserver(clip,FileObserver.ALL_EVENTS) {
            @Override
            public void onEvent(int event, @Nullable String s) {
                if (event==FileObserver.MODIFY){
                    recordingState = true;
                    if (start==0){
                        start = System.currentTimeMillis();
                        timer.start();
                    }

                }
                if (event == FileObserver.CLOSE_WRITE) {
                    recordingState = false;
                    fileObserver.stopWatching();
                    if (timer!=null){
                        timer.onFinish();
                    }
                }
            }
        };
        recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int what, int i1) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    progressView.setVisibility(View.GONE);
                    if (timer!=null){
                        timer.onFinish();
                        timer=null;
                    }
                    recording = false;
                    try {
                        recorder.stop();
                        recorder.release();
                        clips.add(clip);
                        saveRecording();
                    }catch (Exception e){

                    }
                }
            }
        });
        recorder.setOutputFile(clip);
        recorder.setMaxDuration(maxTimeRecord);

        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    final MainActivity main = this;
    public void saveRecording(){
        new AsyncTask<Void, Void, Void>() {
            protected void onPreExecute() {
                System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
                System.setProperty("org.bytedeco.javacpp.maxbytes", "0");
                progressView.setVisibility(View.VISIBLE);
            }
            boolean success = true;
            protected Void doInBackground(Void... unused) {
                FFmpegFrameRecorder frameRecorder = null;
                FFmpegFrameFilter frameFilter=null;
                for (String file:clips){
                    try {
                        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file);
                        grabber.start();
                        String filter = "";
                        double w = grabber.getImageWidth(),h=grabber.getImageHeight();
                        double W = getWindowManager().getDefaultDisplay().getWidth(), H = getWindowManager().getDefaultDisplay().getHeight();
                        if (orientation==90) {
                            filter = "transpose=clock,scale="+grabber.getImageHeight()+":"+grabber.getImageWidth();
                            h = grabber.getImageWidth();w=grabber.getImageHeight();
                        }else if (orientation==180){
                            filter = "vflip,hflip";
                        }else if (orientation==270){
                            filter="transpose=cclock,scale="+grabber.getImageHeight()+":"+grabber.getImageWidth();
                            h = grabber.getImageWidth();w=grabber.getImageHeight();
                        }
                        //calculate crop
                        String crop="";
                        double x=0,y=0,nh=h,nw=w;
                        if (w/W>h/H){
                            nw = h*W/H;
                            x=(w-nw)/2;
                            crop="crop="+nw+":"+nh+":"+x+":"+y;

                        }else if (w/W<h/H){
                            nh = w*H/W;
                            y=(h-nh)/2;
                            crop="crop="+nw+":"+nh+":"+x+":"+y;
                        }
                        if (!filter.equals("") && !crop.equals("")){
                            filter+=","+crop;
                        }else if (!crop.equals("")){
                            filter=crop;
                        }

                        if (frameRecorder==null) {
                            mVideoOutputFile = new File(makeUriString("mp4"));
                            Log.i("MainActivity","File: "+mVideoOutputFile.getAbsolutePath());
                            frameRecorder = new FFmpegFrameRecorder(mVideoOutputFile.getAbsolutePath(),(int)nw,(int)nh);
                            frameRecorder.setFrameRate(grabber.getFrameRate());
                            frameRecorder.setSampleRate(grabber.getSampleRate());
                            frameRecorder.setFormat(grabber.getFormat());
                            frameRecorder.setVideoCodec(grabber.getVideoCodec());
                            frameRecorder.setAudioChannels(grabber.getAudioChannels());
                            frameRecorder.setVideoQuality(18);
                            frameRecorder.setVideoOption("preset", "ultrafast");
                            frameRecorder.start();

                            if (!filter.equals("")) {
                                frameFilter = new FFmpegFrameFilter(filter, grabber.getImageWidth(), grabber.getImageHeight());
                                frameFilter.setFrameRate(frameRecorder.getFrameRate());
                                frameFilter.start();
                            }
                        }

                        Frame frame,filteredFrame;
                        while ((frame=grabber.grab())!=null){
                            if (frame.image!=null) {
                                if (!filter.equals("")) {
                                    frameFilter.push(frame);
                                    filteredFrame = frameFilter.pull();
                                    frameRecorder.record(filteredFrame, frameRecorder.getPixelFormat());
                                } else {
                                    frameRecorder.record(frame);
                                }
                            }else{
                                if (!boomerang){
                                    frameRecorder.record(frame);
                                }
                            }
                        }
                        grabber.stop();
                        grabber.release();
                        new File(file).deleteOnExit();
                    }catch (Exception e){
                        Log.i("merge clips",e.getMessage());
                        success = false;
                    }
                }
                if (frameRecorder!=null){
                    try {
                        new File(clip).deleteOnExit();
                        frameRecorder.stop();
                        frameRecorder.release();
                        frameFilter.stop();
                        frameFilter.release();
                    }catch (Exception e){
                        Log.i("recorder.stop()",e.getMessage());
                    }
                }
                if (boomerang){
                    try {
                        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(mVideoOutputFile);
                        List<Frame> loop = new ArrayList<Frame>();

                        grabber.start();
                        File boomFile = new File(makeUriString("mp4"));
                        Log.i("MainActivity","File: "+boomFile.getAbsolutePath());
                        FFmpegFrameRecorder frecorder = new FFmpegFrameRecorder(boomFile, grabber.getImageWidth(), grabber.getImageHeight());
                        frecorder.setFrameRate(grabber.getFrameRate());
                        frecorder.setSampleRate(grabber.getSampleRate());
                        frecorder.setFormat(grabber.getFormat());
                        frecorder.setVideoCodec(grabber.getVideoCodec());
                        frecorder.setAudioChannels(grabber.getAudioChannels());
                        frecorder.setVideoOption("preset", "ultrafast");
                        frecorder.setVideoQuality(18);

                        frecorder.start();

                        Frame frame;
                        double max = Math.round(2 * grabber.getFrameRate());
                        while ((frame = grabber.grabImage()) != null) {
                            int current = grabber.getFrameNumber();
                            if (current >= max) {
                                break;
                            } else {
                                if (current % 2 == 0) {
                                    loop.add(frame.clone());
                                }
                            }

                        }
                        frame = null;
                        grabber.stop();
                        grabber.release();
                        for (int k = 0; k < 3; k++) {
                            for (Frame frame1 : loop) {
                                frecorder.record(frame1);
                            }
                            for (int i=loop.size()-1;i>=0;i--){
                                frecorder.record(loop.get(i));
                            }
                        }
                        loop.clear();loop=null;
                        frecorder.stop();
                        frecorder.release();
                        mVideoOutputFile.deleteOnExit();
                        mVideoOutputFile = boomFile;
                    }catch (Exception e){

                    }
                }
                return null;
            }
            protected void onPostExecute(Void unused) {
                Log.i("MainActivity",mVideoOutputFile.getAbsolutePath());
                progressView.setVisibility(View.GONE);
                Toast.makeText(main,"File: "+mVideoOutputFile.getAbsolutePath()+"!",Toast.LENGTH_LONG).show();
                releaseCamera();
            }
        }.execute();
    }

    public int getCorrectCameraOrientation(Camera.CameraInfo info, Camera camera) {

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch(rotation){
            case Surface.ROTATION_0:
                degrees = 0;
                break;

            case Surface.ROTATION_90:
                degrees = 90;
                break;

            case Surface.ROTATION_180:
                degrees = 180;
                break;

            case Surface.ROTATION_270:
                degrees = 270;
                break;

        }

        int result;
        if(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT){
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        }else{
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }

    public void releaseCamera(){
        try{recorder.stop();}catch (Exception e){}
        try{recorder.release();}catch (Exception e){}
        try{camera.stopPreview();}catch (Exception e){}
        try{camera.release();}catch (Exception e){}
        usecamera = true;
        previewRunning=false;
        recording=false;
        camera=null;
        recorder=null;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}
