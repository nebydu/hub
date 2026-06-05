package com.monitoring.hub.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * envelope 헤더 키 상수 단일 진실 지점. spec §2.2 / envelope 정본 §2.3.
 *
 * <p>producer(발행)와 ingest(소비) 양쪽이 같은 헤더 키 문자열을 공유하기 위해
 * 중립 패키지에 둔다 — producer/ingest 어느 한쪽에 두면 모듈 경계 역의존이 생긴다.
 * 문자열 값은 데모 spec §2.2와 1바이트도 동일해야 한다(회귀 0).
 */
public final class EnvelopeHeaders {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeHeaders.class);

    public static final String X_MESSAGE_ID = "x-message-id";
    public static final String X_MESSAGE_VERSION = "x-message-version";
    public static final String X_SOURCE = "x-source";
    public static final String X_TRACE_ID = "x-trace-id";

    /**
     * x-source의 "알려진 값" 목록. envelope 정본 §2.3.
     *
     * <p><b>비규범(정보 제공용)이며 검증 대조 대상이 아니다.</b> x-source는 폐쇄
     * enum이 아니므로, 이 목록에 없는 값이 와도 consumer는 깨지지 않아야 한다.
     * 이 Set은 오직 "미지값일 때 debug 로깅을 남길지" 판단에만 쓰인다.
     */
    private static final Set<String> KNOWN_SOURCES = Set.of(
            "script-agent", "monitoring-be", "otel-collector", "infra-agent", "rule-engine");

    private EnvelopeHeaders() {
    }

    /**
     * 주어진 x-source 값이 알려진 값 목록에 포함되는지 여부.
     *
     * <p>가드 목적은 "미지값에 안 깨지는 것을 의도된 동작으로 고정"하는 것이며,
     * 이 메서드는 그 판단(미지값 debug 로깅 여부)에만 쓴다. false라고 해서
     * 처리를 거부하면 안 된다.
     */
    public static boolean isKnownSource(String source) {
        return source != null && KNOWN_SOURCES.contains(source);
    }

    /**
     * 수신 레코드의 x-source 헤더를 읽어, 미지값이면 debug 로깅만 남기는 가드.
     * envelope 정본 §2.3.
     *
     * <p><b>처리 흐름에 절대 영향을 주지 않는다.</b> 헤더 부재·미지값이어도
     * reject/throw/return 하지 않으며, payload 파싱·적재는 호출 측에서 그대로
     * 계속한다. 미지값에 깨지지 않는 것이 의도된 동작이다.
     *
     * @param headers 수신 레코드 헤더({@code record.headers()})
     * @param context 로그 식별용 짧은 문맥(예: 토픽/consumer 이름)
     */
    public static void inspectSource(Headers headers, String context) {
        if (headers == null) {
            return;
        }
        Header header = headers.lastHeader(X_SOURCE);
        if (header == null || header.value() == null) {
            // 헤더 부재는 정상 — 별도 로깅 없이 흘려보낸다.
            return;
        }
        String source = new String(header.value(), StandardCharsets.UTF_8);
        if (!isKnownSource(source)) {
            // 미지값도 정상 처리 대상. 관측용 debug 로깅만 남긴다.
            log.debug("unknown x-source observed: source={} context={}", source, context);
        }
    }
}
