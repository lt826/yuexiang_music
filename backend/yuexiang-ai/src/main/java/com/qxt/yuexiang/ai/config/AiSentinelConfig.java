package com.qxt.yuexiang.ai.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AI 服务 Sentinel 规则（代码声明，重启生效）：
 * QPS 限流 + 热点参数限流（按 userId 隔离，单用户刷不动整体额度）。
 */
@Slf4j
@Configuration
public class AiSentinelConfig {

    @PostConstruct
    public void initRules() {
        // 全局 QPS 限流：单机 5 QPS（单次对话可能串起多次 LLM 调用，从严）
        FlowRule qpsRule = new FlowRule();
        qpsRule.setResource("ai-chat");
        qpsRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        qpsRule.setCount(5);

        // 热点参数限流：同一 userId 每秒最多 2 次（参数索引 0 = userId）
        ParamFlowRule hotRule = new ParamFlowRule("ai-chat")
                .setParamIdx(0)
                .setCount(2);

        com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager.loadRules(List.of(qpsRule));
        com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager.loadRules(List.of(hotRule));
        log.info("Sentinel AI 限流规则已加载: QPS=5, 单用户=2/s");
    }
}
