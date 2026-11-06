package megabytesme.minelights.network;

/** Manual, dependency-free state test; run with javac/java on this file and its production peer. */
public final class ServerDownloadNotificationStateTest {
    private ServerDownloadNotificationStateTest() { }

    public static void main(String[] args) {
        startedInWorldReportsStartAndConfirmedReadiness();
        preWorldTransferReportsProgressOnceAfterWorldEntry();
        disabledSettingSuppressesEveryNotice();
        failureCategoryMatchesWhetherTransferStarted();
        runningServerUsesRestartRequiredNotice();
    }

    private static void startedInWorldReportsStartAndConfirmedReadiness() {
        ServerDownloadNotificationState state = new ServerDownloadNotificationState();
        equal(ServerDownloadNotificationState.STARTED, state.beginTransfer(true, true));
        equal(null, state.inProgress(true, true, true));
        equal(ServerDownloadNotificationState.FINISHED_STARTED, state.finished(true, true));
        equal(ServerDownloadNotificationState.START_FAILED, state.finished(true, false));
    }

    private static void preWorldTransferReportsProgressOnceAfterWorldEntry() {
        ServerDownloadNotificationState state = new ServerDownloadNotificationState();
        equal(null, state.beginTransfer(true, false));
        equal(null, state.inProgress(true, true, false));
        equal(null, state.inProgress(true, false, true));
        equal(ServerDownloadNotificationState.IN_PROGRESS, state.inProgress(true, true, true));
        equal(null, state.inProgress(true, true, true));
        state.reset();
        equal(ServerDownloadNotificationState.STARTED, state.beginTransfer(true, true));
        equal(null, state.inProgress(true, true, true));
    }

    private static void disabledSettingSuppressesEveryNotice() {
        ServerDownloadNotificationState state = new ServerDownloadNotificationState();
        equal(null, state.beginTransfer(false, true));
        equal(null, state.inProgress(false, true, true));
        equal(null, state.finished(false, true));
        equal(null, state.failed(false, true));
    }

    private static void failureCategoryMatchesWhetherTransferStarted() {
        ServerDownloadNotificationState state = new ServerDownloadNotificationState();
        equal(ServerDownloadNotificationState.DOWNLOAD_FAILED, state.failed(true, true));
        equal(ServerDownloadNotificationState.UPDATE_CHECK_FAILED, state.failed(true, false));
    }

    private static void runningServerUsesRestartRequiredNotice() {
        ServerDownloadNotificationState state = new ServerDownloadNotificationState();
        equal(ServerDownloadNotificationState.RESTART_REQUIRED, state.restartRequired(true));
        equal(null, state.restartRequired(false));
    }

    private static void equal(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
