package com.qxt.yuexiang.ai.controller;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.qxt.yuexiang.ai.dto.AiChatRequest;
import com.qxt.yuexiang.ai.service.AiRadioService;
import com.qxt.yuexiang.ai.service.AiSearchService;
import com.qxt.yuexiang.ai.vo.AiChatVO;
import com.qxt.yuexiang.ai.vo.RadioTuneVO;
import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能搜歌接口。
 * /ai/chat 包裹 Sentinel 资源「ai-chat」：单机 5 QPS + 30 并发防护，
 * 被限流时返回统一 429（DeepSeek 按 token 计费，防刷是硬需求）。
 * /ai/radio/tune 心情电台调频：只读官方节目单 + 时间取模，不调 AI，匿名可看。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    /** Sentinel 资源名 */
    private static final String RESOURCE_AI_CHAT = "ai-chat";

    private final AiSearchService aiSearchService;
    private final AiRadioService aiRadioService;

    /**
     * GET /ai/radio/tune?channel=sleep 心情电台调频。
     * 返回频道节目单与当前应播位置（第几首+第几秒）——所有用户同一时刻结果一致。
     */
    @GetMapping("/radio/tune")
    public Result<RadioTuneVO> tune(@RequestParam("channel") String channel) {
        return Result.success(aiRadioService.tune(channel));
    }

    /** POST /ai/chat 自然语言搜歌对话（登录用户可用） */
    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody AiChatRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 个人数据工具（听歌周报等）依赖用户身份，由网关透传
        request.setUserId(userId);
        Entry entry = null;
        try {
            // userId 作热点参数：限流按用户隔离，单用户刷不动整体额度
            entry = SphU.entry(RESOURCE_AI_CHAT, EntryType.IN, 1, userId);
            long start = System.currentTimeMillis();
            AiChatVO vo = aiSearchService.chat(request);
            log.info("AI 对话完成: 耗时={}ms, 命中歌曲={}首", System.currentTimeMillis() - start,
                    vo.getSongs() == null ? 0 : vo.getSongs().size());
            return Result.success(vo);
        } catch (BlockException e) {
            log.warn("AI 接口触发限流: userId={}", userId);
            return Result.fail(ResultCode.TOO_MANY_REQUESTS.getCode(), "AI 助手太忙了，请稍后再试");
        } catch (Exception e) {
            // 业务异常计入 Sentinel 异常统计（异常比例熔断的信号源）
            Tracer.trace(e);
            throw e;
        } finally {
            if (entry != null) {
                entry.exit(1, userId);
            }
        }
    }
}
