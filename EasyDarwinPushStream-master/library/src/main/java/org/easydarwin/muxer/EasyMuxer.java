package org.easydarwin.muxer;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import org.easydarwin.bus.StartRecord;
import org.easydarwin.bus.StopRecord;
import org.easydarwin.easypusher.BuildConfig;
import org.easydarwin.easypusher.EasyApplication;
import org.easydarwin.push.EasyPusher;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by John on 2017/1/10.
 */

public class EasyMuxer {

    private static final boolean VERBOSE = BuildConfig.DEBUG;
    private static final String TAG = EasyMuxer.class.getSimpleName();
    private final String mFilePath;
    private MediaMuxer mMuxer;
    private final long durationMillis;
    private int index = 0;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private long mBeginMillis;
    private MediaFormat mVideoFormat;
    private MediaFormat mAudioFormat;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public EasyMuxer(String path, long durationMillis) throws IOException {
        if(path.endsWith(".mp4")) {
            path = path.substring(0, path.lastIndexOf(".mp4"));
        }
        mFilePath = path;
        this.durationMillis = durationMillis;
        mMuxer = new MediaMuxer(path + "_" + index++ + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public synchronized void addTrack(MediaFormat format, boolean isVideo) {
        // now that we have the Magic Goodies, start the muxer
        if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1)
            throw new RuntimeException("already add all tracks");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            int track = mMuxer.addTrack(format);
            if (VERBOSE)
                Log.i(TAG, String.format("addTrack %s result %d", isVideo ? "video" : "audio", track));
            if (isVideo) {
                mVideoFormat = format;
                mVideoTrackIndex = track;
                if (mAudioTrackIndex != -1) {
                    if (VERBOSE)
                        Log.i(TAG, "both audio and video added,and muxer is started");
                    mMuxer.start();
                    mBeginMillis = System.currentTimeMillis();
                }
            } else {
                mAudioFormat = format;
                mAudioTrackIndex = track;
                if (mVideoTrackIndex != -1) {
                    mMuxer.start();
                    mBeginMillis = System.currentTimeMillis();
                }
            }
        }
    }

    public synchronized void pumpStream(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, boolean isVideo) {
        if (mAudioTrackIndex == -1 || mVideoTrackIndex == -1) {
            Log.i(TAG, String.format("pumpStream [%s] but muxer is not start.ignore..", isVideo ? "video" : "audio"));
            return;
        }
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
        } else if (bufferInfo.size != 0) {
            if (isVideo && mVideoTrackIndex == -1) {
                throw new RuntimeException("muxer hasn't started");
            }

            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mMuxer.writeSampleData(isVideo ? mVideoTrackIndex : mAudioTrackIndex, outputBuffer, bufferInfo);
            }
            if (VERBOSE)
                Log.d(TAG, String.format("sent %s [" + bufferInfo.size + "] with timestamp:[%d] to muxer", isVideo ? "video" : "audio", bufferInfo.presentationTimeUs / 1000));
        }

        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            if (VERBOSE)
                Log.i(TAG, "BUFFER_FLAG_END_OF_STREAM received");
        }

        if (System.currentTimeMillis() - mBeginMillis >= durationMillis && isVideo && ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if (VERBOSE)
                    Log.i(TAG, String.format("record file reach expiration.create new file:" + index));
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
                mVideoTrackIndex = mAudioTrackIndex = -1;
                try {
                    mMuxer = new MediaMuxer(mFilePath + "-" + ++index + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    addTrack(mVideoFormat, true);
                    addTrack(mAudioFormat, false);
                    pumpStream(outputBuffer, bufferInfo, isVideo);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized void release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (mMuxer != null) {
                if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1) {
                    if (VERBOSE)
                        Log.i(TAG, String.format("muxer is started. now it will be stoped."));
                    try {
                        mMuxer.stop();
                        mMuxer.release();
                    } catch (IllegalStateException ex) {
                        ex.printStackTrace();
                    }

                    if (System.currentTimeMillis() - mBeginMillis <= 1500){
                        new File(mFilePath + "-" + index + ".mp4").delete();
                    }
                    mAudioTrackIndex = mVideoTrackIndex = -1;
//                    EasyApplication.BUS.post(new StopRecord());
                }
            }
        }
    }
}
