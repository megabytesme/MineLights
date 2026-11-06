package megabytesme.minelights.network;

/** Thread-safe chat-notification state for one managed-server download. */
public final class ServerDownloadNotificationState {
    public static final String STARTED = "message.minelights.server.download.started";
    public static final String IN_PROGRESS = "message.minelights.server.download.in_progress";
    public static final String FINISHED_STARTED = "message.minelights.server.download.finished_started";
    public static final String RESTART_REQUIRED = "message.minelights.server.download.restart_required";
    public static final String START_FAILED = "message.minelights.server.download.start_failed";
    public static final String DOWNLOAD_FAILED = "message.minelights.server.download.failed";
    public static final String UPDATE_CHECK_FAILED = "message.minelights.server.update_check_failed";

    private boolean startedInWorld;
    private boolean progressMessageSent;

    /** Called when the executable transfer actually begins, after the HTTP response is accepted. */
    public synchronized String beginTransfer(boolean notificationsEnabled, boolean playerInWorld) {
        startedInWorld = playerInWorld;
        progressMessageSent = false;
        return notificationsEnabled && playerInWorld ? STARTED : null;
    }

    /** Returns one in-progress notice only if the transfer began before the player entered a world. */
    public synchronized String inProgress(boolean notificationsEnabled, boolean transferActive, boolean playerInWorld) {
        if (!notificationsEnabled || !transferActive || !playerInWorld || startedInWorld || progressMessageSent) {
            return null;
        }
        progressMessageSent = true;
        return IN_PROGRESS;
    }

    /** The success notice is returned only after the managed server is confirmed ready. */
    public synchronized String finished(boolean notificationsEnabled, boolean serverReady) {
        if (!notificationsEnabled) return null;
        return serverReady ? FINISHED_STARTED : START_FAILED;
    }

    /** The verified update is staged but cannot be installed while a shared server is running. */
    public synchronized String restartRequired(boolean notificationsEnabled) {
        return notificationsEnabled ? RESTART_REQUIRED : null;
    }

    public synchronized String failed(boolean notificationsEnabled, boolean setupAttempted) {
        if (!notificationsEnabled) return null;
        return setupAttempted ? DOWNLOAD_FAILED : UPDATE_CHECK_FAILED;
    }

    public synchronized void reset() {
        startedInWorld = false;
        progressMessageSent = false;
    }
}
