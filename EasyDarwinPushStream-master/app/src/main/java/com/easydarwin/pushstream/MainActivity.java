package com.easydarwin.pushstream;

import android.arch.lifecycle.Observer;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.easydarwin.push.MediaStream;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;

/**
 * 主页面，提供摄像头和屏幕推送功能.
 *
 * @author levi
 * @date 2020-3-20
 */

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1000;
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private MediaStream mMediaStream;
    private TextView mPushingStateText  ;
    private Button mPushScreenBtn ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initService();

    }

   private void initView() {
       mPushingStateText = findViewById(R.id.pushing_state);
       mPushScreenBtn = findViewById(R.id.pushing_screen);
   }

    // 初始化推送屏幕服务
    private void initService(){
        // 启动服务
        Intent intent = new Intent(this, MediaStream.class);
        startService(intent);
        getMediaStream().subscribe(new Consumer<MediaStream>() {
            @Override
            public void accept(final MediaStream ms) throws Exception {

                ms.observePushingState(MainActivity.this, new Observer<MediaStream.PushingState>() {

                    @Override
                    public void onChanged(@Nullable MediaStream.PushingState pushingState) {
                        if (pushingState.screenPushing) {
                            mPushingStateText.setText(getString(R.string.push_url));
                            // 更改屏幕推送按钮状态.
                            if (pushingState.state > 0) {
                                mPushScreenBtn.setText(getString(R.string.cancel_push_screen));
                            } else {
                                mPushScreenBtn.setText(getString(R.string.push_screen));
                            }
                            mPushScreenBtn.setEnabled(true);
                        }
                        mPushingStateText.append(":\t" + pushingState.msg);
                        if (pushingState.state > 0) {
                            mPushingStateText.append(pushingState.url);
                        }

                    }
                });
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_PERMISSION);
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Toast.makeText(MainActivity.this, getString(R.string.server_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Single<MediaStream> getMediaStream() {
        Single<MediaStream> single = RxHelper.single(MediaStream.getBindedMediaStream(this, this), mMediaStream);
        if (mMediaStream == null) {
            return single.doOnSuccess(new Consumer<MediaStream>() {
                @Override
                public void accept(MediaStream ms) throws Exception {
                    mMediaStream = ms;
                }
            });
        } else {
            return single;
        }
    }

    // 推送屏幕.
    public void onPushScreen(final View view) {
        getMediaStream().subscribe(new Consumer<MediaStream>() {
            @Override
            public void accept(MediaStream mediaStream) throws Exception {
                MediaStream.PushingState state = mediaStream.getScreenPushingState();
                if (state != null && state.state > 0) {
                    // 取消推送。
                    mediaStream.stopPushScreen();
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        return;
                    }
                    MediaProjectionManager mMpMngr = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mMpMngr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
                    // 防止点多次.
                    view.setEnabled(false);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION: {
                if (grantResults.length > 1
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    getMediaStream().subscribe(new Consumer<MediaStream>() {
                        @Override
                        public void accept(MediaStream mediaStream) throws Exception {
                            mediaStream.notifyPermissionGranted();
                        }
                    });
                } else {
                    finish();
                }
                break;
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            getMediaStream().subscribe(new Consumer<MediaStream>() {
                @Override
                public void accept(MediaStream mediaStream) throws Exception {
                    mediaStream.pushScreen(resultCode, data, AppConstants.HOST, AppConstants.PORT, AppConstants.ID);
                }
            });
        }
    }

}
