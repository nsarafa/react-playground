package com.pauselabs.pause.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.pauselabs.R;
import com.pauselabs.pause.Injector;
import com.pauselabs.pause.PauseApplication;
import com.pauselabs.pause.core.Constants;
import com.pauselabs.pause.listeners.NotificationActionListener;
import com.pauselabs.pause.listeners.PausePhoneStateListener;
import com.pauselabs.pause.listeners.PauseSmsListener;
import com.pauselabs.pause.models.PauseBounceBackMessage;
import com.pauselabs.pause.models.PauseSession;
import com.pauselabs.pause.ui.ScoreboardActivity;
import com.squareup.otto.Bus;

import javax.inject.Inject;
import java.util.Date;
import java.util.Random;

/**
 * Service initiates Pause Listeners
 */
public class PauseSessionService extends Service{

    private static final String TAG = PauseSessionService.class.getSimpleName();

    @Inject protected Bus eventBus;
    @Inject NotificationManager notificationManager;

    private PauseSmsListener smsListener = new PauseSmsListener();
    private PausePhoneStateListener phoneListener = new PausePhoneStateListener();
    private boolean sessionRunning = false;
    private boolean sessionStarted;

    private Date mEndTime;
    private Date mStartTime;
    private PauseSession mActiveSession;
    private PauseBounceBackMessage mActivePauseBounceBack;

    private boolean mStophandler = false;

    private AudioManager am;

    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if(!mStophandler) {
                notifyPauseSessionRunning();
                handler.postDelayed(this, 1000);
            }
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();

        Injector.inject(this);

        // Register the bus so we can send notifcations
        eventBus.register(this);

        am = (AudioManager)getSystemService(AUDIO_SERVICE);
    }

    @Override
    public void onDestroy() {
        if (PauseApplication.getOldRingerMode() != AudioManager.RINGER_MODE_SILENT)
            am.setRingerMode(PauseApplication.getOldRingerMode());

        mStophandler = true;
        handler.removeCallbacks(runnable);

        // Unregister bus, since its not longer needed as the service is shutting down
        eventBus.unregister(this);

        // unregister receiver(s)
        unregisterReceiver(smsListener);
        unregisterReceiver(phoneListener);

        notificationManager.cancel(Constants.Notification.SESSION_NOTIFICATION_ID);

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PauseApplication.setOldRingerMode(am.getRingerMode());
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);

        if(!sessionStarted) {

            sessionStarted = true;

            startPauseSession();
            // Run as foreground service: http://stackoverflow.com/a/3856940/5210
            // Another example: https://github.com/commonsguy/cw-android/blob/master/Notifications/FakePlayer/src/com/commonsware/android/fakeplayerfg/PlayerService.java
            String message = "";
            if (mActiveSession.getCreator() == Constants.Session.Creator.CUSTOM)
                message = getString(R.string.pause_session_running);
            else if (mActiveSession.getCreator() == Constants.Session.Creator.SILENCE)
                message = getString(R.string.pause_session_running_silence);
            else if (mActiveSession.getCreator() == Constants.Session.Creator.DRIVE)
                message = getString(R.string.pause_session_running_drive);
            else if (mActiveSession.getCreator() == Constants.Session.Creator.SLEEP)
                message = getString(R.string.pause_session_running_sleep);
            startForeground(Constants.Notification.SESSION_NOTIFICATION_ID, getNotification(message));
        }

        return Service.START_NOT_STICKY; // Service will not be restarted if android kills it
    }

    private void startPauseSession() {

        // start SMS receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.Message.SMS_RECEIVED_INTENT);
        registerReceiver(smsListener, filter);

        // start Phone Call receiver
        IntentFilter phoneStateFilter = new IntentFilter();
        phoneStateFilter.addAction(Constants.Message.PHONE_STATE_CHANGE_INTENT);
        registerReceiver(phoneListener, phoneStateFilter);

        // Retrieve Pause end time
        mActiveSession = PauseApplication.getCurrentSession();
        mActivePauseBounceBack = mActiveSession.getActiveBounceBackMessage();
        mEndTime = new Date(mActivePauseBounceBack.getEndTime());

        mStartTime = new Date();


        //notifyPauseSessionRunning();
        runnable.run();
    }

    private void notifyPauseSessionRunning() {
        Date currentDate = new Date();
        if(currentDate.getTime() > mEndTime.getTime()){
//            // timer has expired
//            stopPauseSession();

            // display results dialog
            mStophandler = true;

            updateNotification(getString(R.string.pause_session_ended));

        }
        else{
            long diff = mEndTime.getTime() - currentDate.getTime();
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            updateNotification(hours % 24 + "h " + minutes % 60 + "m " + seconds % +60 + "s remaining");
        }

        //updateNotification(getString(R.string.pause_session_running));
    }

    private void updateNotification(String message) {
        notificationManager.notify(Constants.Notification.SESSION_NOTIFICATION_ID, getNotification(message));
    }

    /**
     * Creates a notification to show in the notification bar
     *
     * @param message the message to display in the notification bar
     * @return a new {@link Notification}
     */
    private Notification getNotification(String message) {
        final Intent i = new Intent(this, ScoreboardActivity.class);

        // open activity intent
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, i, 0);

        // stop session intent
        Intent stopPauseIntent = new Intent(this, NotificationActionListener.class);
        stopPauseIntent.putExtra(Constants.Notification.PAUSE_NOTIFICATION_INTENT, Constants.Notification.STOP_PAUSE_SESSION);
        PendingIntent stopPausePendingIntent = PendingIntent.getBroadcast(this, new Random().nextInt(), stopPauseIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // edit session intent
        Intent editPauseIntent = new Intent(this, NotificationActionListener.class);
        editPauseIntent.putExtra(Constants.Notification.PAUSE_NOTIFICATION_INTENT, Constants.Notification.EDIT_PAUSE_SESSION);
        editPauseIntent.putExtra(Constants.Pause.EDIT_PAUSE_MESSAGE_ID_EXTRA, mActivePauseBounceBack.getId());
        PendingIntent editPausePendingIntent = PendingIntent.getBroadcast(this, new Random().nextInt(), editPauseIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // edit session intent
        Intent notDriverPauseIntent = new Intent(this, NotificationActionListener.class);
        notDriverPauseIntent.putExtra(Constants.Notification.PAUSE_NOTIFICATION_INTENT, Constants.Notification.NOT_DRIVER_PAUSE_SESSION);
        PendingIntent notDriverPausePendingIntent = PendingIntent.getBroadcast(this, new Random().nextInt(), notDriverPauseIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder notBuilder = new NotificationCompat.Builder(this);

        notBuilder.setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_stat_pause_icon_pause)
                .setContentText(message)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        switch (mActiveSession.getCreator()) {
            case Constants.Session.Creator.CUSTOM:
                notBuilder.addAction(R.drawable.ic_stat_notificaiton_end, "End", stopPausePendingIntent);
                notBuilder.addAction(R.drawable.ic_stat_notification_pencil, "Edit", editPausePendingIntent);

                break;
        }

        return notBuilder.build();
    }


    public IBinder onBind(Intent intent) {
        return null;
    }


}
