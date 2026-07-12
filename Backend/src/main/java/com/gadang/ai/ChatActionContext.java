package com.gadang.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 한 번의 챗 요청 동안 Tool 들이 만들어 낸 {@link ChatAction} 을 모으는 요청 단위 수집기.
 *
 * <p>Spring AI 기본 Tool 실행은 요청(Tomcat) 스레드에서 동기로 일어나므로
 * ThreadLocal 로 안전하게 누적·회수할 수 있다.
 * 컨트롤러가 {@link #start()} → ChatClient 호출 → {@link #drain()} 순서로 사용한다.
 */
public final class ChatActionContext {

    private static final ThreadLocal<List<ChatAction>> HOLDER = new ThreadLocal<>();

    private ChatActionContext() {}

    /** 요청 시작 시 수집 버퍼 초기화. */
    public static void start() {
        HOLDER.set(new ArrayList<>());
    }

    /** Tool 내부에서 액션 추가 (수집 중이 아니면 무시). */
    public static void add(String type, String label, String route, Map<String, String> query) {
        List<ChatAction> list = HOLDER.get();
        if (list != null) {
            list.add(new ChatAction(type, label, route, query));
        }
    }

    /** 수집된 액션을 반환하고 버퍼를 비운다. */
    public static List<ChatAction> drain() {
        List<ChatAction> list = HOLDER.get();
        HOLDER.remove();
        return list == null ? List.of() : list;
    }
}
