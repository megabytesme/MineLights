package megabytesme.minelights.accessor;

public interface ChatReceivedAccessor {
    boolean wasChatReceivedThisTick();
    void resetChatReceivedFlag();
}