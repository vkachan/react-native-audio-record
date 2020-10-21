package com.goodatlas.audiorecord;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.util.Base64;
import android.util.Log;

import android.media.*;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.util.TimerTask;
import java.util.Timer;

public class RNAudioRecordModule extends ReactContextBaseJavaModule {

    private final String TAG = "RNAudioRecord";
    private final ReactApplicationContext reactContext;

    private static final int RECORDER_BPP = 16;
    private static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final String E_RECORDING_ERROR = "E_RECORDING_ERROR";

    private AudioRecord recorder;
    private int bufferSize;
    private int cAmplitude = 0;
    private boolean isRecording;

    private String tmpFile;
    private String outFile;

    private Thread recordingThread = null;

    public RNAudioRecordModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNAudioRecord";
    }

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    @ReactMethod
    public void init(ReadableMap options) {
        String fileDir = getReactApplicationContext().getFilesDir().getAbsolutePath();
        if (options.hasKey("wavFileDir")) {
            fileDir = options.getString("wavFileDir");
        }

        outFile = fileDir + "/" + "audio.wav";
        tmpFile = fileDir + "/" + "temp.pcm";
        if (options.hasKey("wavFile")) {
            String fileName = options.getString("wavFile");
            outFile = fileDir + "/" + fileName;
        }

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        isRecording = false;
        new Timer().scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                if (isRecording) {
                    WritableMap params = Arguments.createMap();
                    params.putString("current", Integer.toString(cAmplitude < 0 ? cAmplitude * -1 : cAmplitude));
                    sendEvent(reactContext, "onGetMaxAmplitude", params);
                }
            }
        }, 0, 50);
    }

    private short getShort(byte argB1, byte argB2) {
        return (short) (argB1 | (argB2 << 8));
    }

    private void startRecording(final boolean b) {
        Log.d("RNAudioRecordModule", "startRecording");
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioDataToFile(b);
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private boolean stopRecording(boolean b) {
        if (recorder != null) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }

        if (b == true) {
            copyWaveFile(tmpFile, outFile);
            deleteTempFile();
        }
        return true;
    }

    private void writeAudioDataToFile(boolean b) {
        byte data[] = new byte[bufferSize];
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(tmpFile, b);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int read = 0;

        if (os != null) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                        for (int i = 0; i < data.length / 2; i++) { // 16bit
                            final short curSample = getShort(data[i * 2],
                                    data[i * 2 + 1]);
                            cAmplitude = curSample;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void copyWaveFile(String inFilename, String outFilename) {
        Log.d("RNAudioRecordModule", "copyWaveFile " + inFilename + " " + outFilename);
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 44;
        long longSampleRate = RECORDER_SAMPLE_RATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLE_RATE * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename, true);
            totalAudioLen = in.getChannel().size() + out.getChannel().size();
            totalDataLen = totalAudioLen + 44;

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate, outFilename);

            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate, String outFileName) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        RandomAccessFile rFile = new RandomAccessFile(outFileName, "rw");
        rFile.seek(0);
        rFile.write(header, 0, 44);
        rFile.close();
    }

    private void deleteTempFile() {
        File file = new File(tmpFile);
        file.delete();
    }

    @ReactMethod
    public void getMaxAmplitude(Promise promise) {
        promise.resolve((isRecording ? (cAmplitude < 0 ? cAmplitude * -1 : cAmplitude) : -1) + "");
    }

    @ReactMethod
    public void start() {
        startRecording(false);
    }

    @ReactMethod
    public void stop(Promise promise) {
        if (stopRecording(true)) {
            promise.resolve(outFile);
        } else {
            promise.reject(E_RECORDING_ERROR);
        }
    }

    @ReactMethod
    public void pause() {
        stopRecording(false);
    }

    @ReactMethod
    public void resume() {
        startRecording(true);
    }
}
