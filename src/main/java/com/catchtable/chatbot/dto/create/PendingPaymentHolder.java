package com.catchtable.chatbot.dto.create;

public class PendingPaymentHolder {
    private static final ThreadLocal<PendingPaymentInfo> holder = new ThreadLocal<>();

    public static void set(PendingPaymentInfo info) {
        holder.set(info);
    }

    public static PendingPaymentInfo get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}
