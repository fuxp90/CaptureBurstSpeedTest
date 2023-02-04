package com.cdts.synccapture;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;

import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecordController {

    /**
     * This is the highest sampling rate supported by Pixel-3 and Nexus-5X.
     * 44100 can also be used which is supported by a large set of Android devices.
     * But with 44100, the audio timestamping precision will degrade as compared to 192000.
     */
    private static final int SAMPLING_RATE_IN_HZ = 192000;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;


    /**
     * We will listen for 10000 beeps, this number can be changed depending on application/usage.
     */
    int listened_beep = 10000;


    /**
     * After capturing audio event, we will not process next 60 buffers read from the audio pipeline.
     * This is a developer decision. For some applications, we might not skip any buffers.
     */
    int pass_buffer = 60;

    /**
     * This is event amplitude. If we read audio buffer value larger than this, then the event has happened
     */
    int max_amplitude = 8000;


    /**
     * We will make the buffer size to the 5 times of the minimum allowed buffer
     */
    private static final int BUFFER_SIZE_FACTOR = 5;

    /**
     * Final buffer size is the minimum allowed buffer size multiplied by the buffer size factor
     */
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;


    /**
     * Used to determined if a recording is in happening (true) or not (false).
     */
    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);


    private AudioRecord recorder = null;
    /**
     * This will record the time when the audio event happened
     */
    long event_time = 0;
    /**
     * This is the thread which will run the main recording loop
     */
    private Thread recordingThread = null;


    private final Context mContext;
    private int mListenedIndex;
    private OnAudiListenedCallback mOnAudiListenedCallback;
    private long mFirstListenedTime;

    public interface OnAudiListenedCallback {
        void onListened(int index, long timestamp, double rateInHz, int maxAmplitude);
    }

    public AudioRecordController(Context context) {
        mContext = context;
    }

    public int getListenedIndex() {
        return mListenedIndex;
    }

    public long getFirstListenedTime() {
        return mFirstListenedTime;
    }

    public void startRecording(OnAudiListenedCallback callback) {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mOnAudiListenedCallback = callback;
        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
        mListenedIndex = 0;
        mFirstListenedTime = 0;
        recorder.startRecording();
        recordingInProgress.set(true);

        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        /**
         Setting the highest priority for this thread
         */
        recordingThread.setPriority(10);
        recordingThread.start();
    }

    public void stopRecording() {
        if (null == recorder) {
            return;
        }

        recordingInProgress.set(false);
        recorder.stop();
        recorder.release();
        recorder = null;
        recordingThread = null;

    }//end stopRecording

    private class RecordingRunnable implements Runnable {

        @Override
        public void run() {

            short[] buffer = new short[BUFFER_SIZE];
            AudioTimestamp at = new AudioTimestamp();

            long prev_timestamp = 0;
            long curr_timestamp = 0;

            long prev_frame_count = 0;
            long curr_frame_count = 0;

            int count_listened = 0;

            int local_pass = 0;

            try {
                while (recordingInProgress.get() && count_listened < listened_beep) {
                    int result = recorder.read(buffer, 0, BUFFER_SIZE, AudioRecord.READ_NON_BLOCKING);
                    /**
                     * Clock monotonic including suspend time or its equivalent on the system,
                     * in the same units and timebase as {@link android.os.SystemClock#elapsedRealtimeNanos}.
                     */
                    recorder.getTimestamp(at, AudioTimestamp.TIMEBASE_BOOTTIME);

                    short max = -32768;
                    int index = 0;

                    prev_timestamp = curr_timestamp;
                    curr_timestamp = at.nanoTime;
                    prev_frame_count = curr_frame_count;
                    curr_frame_count = at.framePosition;

                    int frames_read = (int) (curr_frame_count - prev_frame_count);
                    if (frames_read > 0) {
                        //finding the max value of the audio event.
                        for (int i = 0; i < frames_read && i < BUFFER_SIZE; i++) {
                            short val = buffer[i];
                            if (val < 0) val = (short) -val;
                            if (val > max) {
                                max = val;
                                index = i;
                            }
                        }

                        int time_between_buffers = (int) (curr_timestamp - prev_timestamp);
                        double time_elapsed_for_the_event_double = time_between_buffers * ((index * 1.0) / frames_read);
                        long time_for_event = prev_timestamp + (long) time_elapsed_for_the_event_double;
                        //this is the real rate captured by the device
                        double rate = (1.0 * 1000 * 1000 * 1000 * frames_read) / (time_between_buffers * 1.0);
                        //System.out.println("Frames: "+frames_read+" : Time Difference: "+time_between_buffers+" Rate is:\t"+rate+"\t Max:"+max);
                        //We are clamping the buffer read fluctuations
                        //it is a valid buffer read.
                        if (rate > 189000.0 && rate < 195000.0) {
                            //An event has happened
                            if (max > max_amplitude && local_pass <= 0) {
                                count_listened++;//we have listened a positive beep
                                local_pass = pass_buffer; //we are skipping next pass_buffer buffer frames
                                //This is the event time that is captured
                                event_time = time_for_event;
                                System.out.println("Frames: " + frames_read + " : Time Difference: " + time_between_buffers + " Rate is:\t" + rate + "\t Max:" + max + "\t event_time:" + event_time);

                                if (mFirstListenedTime == 0) {
                                    mFirstListenedTime = event_time;
                                }
                                if (mOnAudiListenedCallback != null) {
                                    mOnAudiListenedCallback.onListened(++mListenedIndex, event_time, rate, max);
                                }

                            }//end  if(rate>189000.0 && rate<195000.0)
                        } else {
                            System.out.println("Rate is not correct:" + rate);
                        }
                        local_pass--;
                    }//end if(curr_frame_count-prev_frame_count>0)

                }// while (recordingInProgress.get()
            } catch (Exception e) {
                throw new RuntimeException("Not able to start the audio thread", e);
            }
        }
    }//end RecordingRunnable
}
