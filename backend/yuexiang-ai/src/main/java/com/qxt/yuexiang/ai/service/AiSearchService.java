package com.qxt.yuexiang.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.ai.dto.AiChatRequest;
import com.qxt.yuexiang.ai.dto.DeepseekChatRequest;
import com.qxt.yuexiang.ai.feign.FavoriteClient;
import com.qxt.yuexiang.ai.feign.PlaylistClient;
import com.qxt.yuexiang.ai.feign.SingerClient;
import com.qxt.yuexiang.ai.feign.SongClient;
import com.qxt.yuexiang.ai.vo.AiChatVO;
import com.qxt.yuexiang.ai.vo.AiSongVO;
import com.qxt.yuexiang.ai.vo.DeepseekChatResponse;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 搜歌编排：DeepSeek Function Calling 循环
 *
 * 流程：
 * 1. 组装 system 提示词 + 历史 + 本轮提问，发起 chat/completions
 * 2. 模型返回 tool_calls → 执行本地工具（Feign 查 song/singer 服务）
 * 3. 工具结果以 role=tool（带 tool_call_id）消息回填，继续对话；最多 MAX_TOOL_ROUNDS 轮
 * 4. finish_reason=stop 的纯文本作为 reply 下发
 * 全程收集工具命中的歌曲，随响应下发供前端直接播放。
 */
@Slf4j
@Service
public class AiSearchService {

    /** 工具循环上限：防止模型反复调工具不收敛（生成歌单需多次检索+建单，比纯找歌多留余量） */
    private static final int MAX_TOOL_ROUNDS = 8;

    /** 每个工具单次返回给模型的歌曲上限（控制 token 与回答聚焦度） */
    private static final int TOOL_SONG_LIMIT = 10;

    /** 带入上下文的历史条数上限 */
    private static final int MAX_HISTORY = 10;

    private static final String SYSTEM_PROMPT = """
            你是"悦享音乐"平台的智能搜歌助手。用户会用自然语言找歌，你负责调用工具在平台曲库中检索，并基于真实结果回答。

            工具说明：
            - search_songs：按关键字搜歌。命中范围包括歌名、歌词、风格、歌手名，适合歌词片段找歌。把用户描述中最有辨识度的片段提取出来传参（如用户说"那句雨下整夜的歌"，就传"雨下整夜"）。歌词找歌时传单句 4~10 字的歌词原文，不要带标点，也不要合并两句。            - get_singer_songs：查某歌手的全部已发布歌曲，传入从用户话中提取的歌手名。
            - get_hot_songs：全站热门榜。用户问"最近流行什么""有什么好听的"且没有具体目标时使用。

            规则：
            1. 明确单一意图通常调一个工具即可；意图含糊时可组合多个工具再综合。
            2. 回答必须基于工具返回的真实数据；绝不编造平台里不存在的歌曲。
            3. 提到任何歌曲时，必须写出完整的歌名和完整的歌手名（照工具返回原文），禁止缩写、省略号、首字母缩写或"某歌"这类指代。
            4. 没有匹配时直说没找到，建议换个说法或关键词。
            5. 找到结果时自然列出「歌名 - 歌手名」，可简短点评风格/热度；中文回答，友好简洁。
            6. 歌词找歌第一次没命中时，若结果带 matchedBy=lyric_compact 说明曲库歌词是逐行存的，换另一句更短、更独特的歌词再试一次（最多重试 2 次）；仍未命中就如实告知。
            7. 用户问"我最近听了什么""我的听歌报告/周报""我听得最多的歌"等个人数据时，调 get_listen_report，基于统计结果生成温暖的个性化解读，不要罗列原始 JSON。若返回 error=no_user_identity 说明用户身份缺失，引导其登录后再问。
            8. 生成歌单：用户描述场景/心情/风格（跑步、学习、睡前、伤感等）并希望"建歌单/生成歌单/做个歌单"时——先用 search_songs 按最有代表性的 1~2 个关键词检索（风格词、场景词，如"民谣""跑步"；不要连环换词反复试搜），凑够 6~15 首后立即调 create_playlist；create_playlist 的 song_ids 只能从本轮其他工具返回过的歌曲 id 中选，title 要贴合场景。歌单创建成功后，回复里说明歌单名、收录了多少首。若用户没说要建歌单，只是找歌，不要调 create_playlist。返回 error=no_user_identity 时引导登录；歌曲不足 6 首时如实告知并问用户是否仍要创建。
            """;

    /** OpenAI 兼容 Function Calling 工具定义 */
    private static final List<DeepseekChatRequest.Tool> TOOLS = buildTools();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeepseekClient deepseekClient;
    private final SongClient songClient;
    private final SingerClient singerClient;
    private final FavoriteClient favoriteClient;
    private final PlaylistClient playlistClient;

    public AiSearchService(DeepseekClient deepseekClient, SongClient songClient, SingerClient singerClient,
                           FavoriteClient favoriteClient, PlaylistClient playlistClient) {
        this.deepseekClient = deepseekClient;
        this.songClient = songClient;
        this.singerClient = singerClient;
        this.favoriteClient = favoriteClient;
        this.playlistClient = playlistClient;
    }

    public AiChatVO chat(AiChatRequest request) {
        String question = request.getMessage() == null ? "" : request.getMessage().trim();
        if (question.isEmpty()) {
            throw new BusinessException(400, "请输入你的问题");
        }

        // 各轮工具命中的歌曲池（按命中顺序去重），随回复下发前端渲染
        List<AiSongVO> hitSongs = new ArrayList<>();
        // 本轮新建歌单信息（create_playlist 工具成功时回填，随响应下发）
        AiChatVO extra = new AiChatVO();

        List<DeepseekChatRequest.Message> messages = new ArrayList<>();
        messages.add(DeepseekChatRequest.Message.of("system", SYSTEM_PROMPT));
        appendHistory(messages, request.getHistory());
        messages.add(DeepseekChatRequest.Message.of("user", question));

        // 工具循环
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            DeepseekChatResponse response = deepseekClient.chat(buildRequest(messages, true));
            DeepseekChatResponse.Choice choice = response == null ? null : response.firstChoice();
            if (choice == null || choice.getMessage() == null) {
                throw new BusinessException(500, "AI 服务暂无响应，请稍后重试");
            }

            List<DeepseekChatResponse.ToolCall> toolCalls = choice.getMessage().getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return done(textOrFallback(choice.getMessage().getContent()), hitSongs, extra);
            }
            messages.add(echoAssistant(toolCalls, choice.getMessage().getContent()));

            for (DeepseekChatResponse.ToolCall toolCall : toolCalls) {
                String resultJson = executeTool(toolCall.getFunction().getName(),
                        toolCall.getFunction().getArguments(), hitSongs, request.getUserId(), extra);
                messages.add(DeepseekChatRequest.Message.toolResult(toolCall.getId(), resultJson));
            }
        }

        // 达到轮次上限仍未收敛：去掉 tools 强制模型用文本总结已有信息
        log.warn("AI 工具循环达到上限 {} 轮，强制总结", MAX_TOOL_ROUNDS);
        DeepseekChatResponse response = deepseekClient.chat(buildRequest(messages, false));
        DeepseekChatResponse.Choice last = response == null ? null : response.firstChoice();
        String reply = last != null && last.getMessage() != null
                ? textOrFallback(last.getMessage().getContent())
                : "抱歉，我一时没能完成检索，请换个说法试试。";
        return done(reply, hitSongs, extra);
    }

    private DeepseekChatRequest buildRequest(List<DeepseekChatRequest.Message> messages, boolean withTools) {
        DeepseekChatRequest req = new DeepseekChatRequest();
        req.setMessages(messages);
        if (withTools) {
            req.setTools(TOOLS);
            req.setToolChoice("auto");
        }
        return req;
    }

    /** 把模型的 tool_calls 决策回显为 assistant 消息（协议要求原样带回） */
    private DeepseekChatRequest.Message echoAssistant(List<DeepseekChatResponse.ToolCall> toolCalls, String content) {
        DeepseekChatRequest.Message msg = new DeepseekChatRequest.Message();
        msg.setRole("assistant");
        msg.setContent(content);
        List<DeepseekChatRequest.ToolCall> echo = new ArrayList<>();
        for (DeepseekChatResponse.ToolCall tc : toolCalls) {
            DeepseekChatRequest.ToolCall call = new DeepseekChatRequest.ToolCall();
            call.setId(tc.getId());
            call.setType("function");
            DeepseekChatRequest.FunctionCall fc = new DeepseekChatRequest.FunctionCall();
            fc.setName(tc.getFunction() != null ? tc.getFunction().getName() : null);
            fc.setArguments(tc.getFunction() != null ? tc.getFunction().getArguments() : "{}");
            call.setFunction(fc);
            echo.add(call);
        }
        msg.setToolCalls(echo);
        return msg;
    }

    /** 执行单个工具调用，结果序列化为喂回模型的 JSON 字符串；命中的歌同时存入 sink 池 */
    private String executeTool(String name, String argumentsJson, List<AiSongVO> sink,
                               Long userId, AiChatVO extra) {
        Map<String, Object> args = parseArgs(argumentsJson);
        log.info("AI 工具调用: {} 参数={}", name, args);
        try {
            Object payload = switch (name) {
                case "search_songs" -> searchSongs(str(args.get("keyword")), sink);
                case "get_singer_songs" -> singerSongs(str(args.get("singer_name")), sink);
                case "get_hot_songs" -> hotSongs(sink);
                case "get_listen_report" -> listenReport(userId);
                case "create_playlist" -> createPlaylist(userId, args, sink, extra);
                default -> Map.of("error", "未知工具: " + name);
            };
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("AI 工具执行失败: {} {}", name, e.getMessage());
            return "{\"error\":\"工具执行失败，请基于现有信息回答\"}";
        }
    }

    /** 建歌单数量上限（防模型一次塞爆） */
    private static final int AI_PLAYLIST_MAX_SONGS = 30;
    private static final int AI_PLAYLIST_MIN_SONGS = 1;

    /**
     * AI 生成歌单：创建歌单 + 逐首加歌。
     * 防幻觉：song_ids 逐一对照本轮工具命中池，池外的直接跳过；
     * 全部无效则不建歌单，返回错误让模型如实告知。
     */
    private Object createPlaylist(Long userId, Map<String, Object> args,
                                  List<AiSongVO> sink, AiChatVO extra) {
        if (userId == null) {
            return Map.of("error", "no_user_identity", "note", "用户未登录，无法创建歌单");
        }
        String title = str(args.get("title")).trim();
        if (title.isEmpty()) {
            title = "我的AI歌单";
        }
        if (title.length() > 50) {
            title = title.substring(0, 50);
        }

        // 参数里的 id 必须命中本轮歌曲池（模型看到的精简视图里带 id，幻觉 ID 池里没有）
        List<Long> requested = new ArrayList<>();
        if (args.get("song_ids") instanceof List<?> rawIds) {
            for (Object o : rawIds) {
                try {
                    requested.add(Long.valueOf(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                    // 脏参数跳过
                }
            }
        }
        Map<Long, AiSongVO> poolById = new LinkedHashMap<>();
        for (AiSongVO s : sink) {
            poolById.putIfAbsent(s.getId(), s);
        }
        List<Long> validIds = requested.stream()
                .distinct()
                .filter(poolById::containsKey)
                .limit(AI_PLAYLIST_MAX_SONGS)
                .collect(Collectors.toList());
        int invalidCount = requested.size() - validIds.size();
        if (validIds.size() < AI_PLAYLIST_MIN_SONGS) {
            return Map.of("error", "no_valid_songs",
                    "note", "song_ids 里没有一首是本轮检索命中过的歌曲，已取消创建；请先用 search_songs 检索");
        }

        // 创建歌单（失败让模型告知，不再继续加歌）
        Result<Long> created = playlistClient.create(userId, Map.of("title", title));
        Long playlistId = created == null ? null : created.getData();
        if (playlistId == null) {
            return Map.of("error", "create_failed", "note", "歌单服务暂时不可用，创建失败");
        }

        // 逐首加歌；单首失败（如歌曲已被下架）不中断整体
        int added = 0;
        for (Long songId : validIds) {
            try {
                playlistClient.addSong(userId, playlistId, songId);
                added++;
            } catch (Exception e) {
                log.warn("AI 歌单加歌失败: playlistId={}, songId={}, {}", playlistId, songId, e.getMessage());
            }
        }
        if (added == 0) {
            return Map.of("error", "add_failed", "note", "歌单已创建但加歌全部失败");
        }

        extra.setCreatedPlaylistId(playlistId);
        extra.setCreatedPlaylistTitle(title);
        log.info("AI 歌单已生成: id={}, title={}, 加入 {} 首（无效参数 {} 个）", playlistId, title, added, invalidCount);
        return Map.of("playlistId", playlistId, "title", title, "added", added,
                "skippedInvalid", invalidCount,
                "note", "创建成功，回复用户歌单名与歌曲数即可");
    }

    /** 听歌周报：透传用户身份查 favorite 服务；未登录/身份缺失返回引导性错误 */
    private Object listenReport(Long userId) {
        if (userId == null) {
            return Map.of("error", "no_user_identity", "note", "用户未登录，无法查询个人听歌数据");
        }
        Result<Map<String, Object>> resp = favoriteClient.weeklyReport(userId);
        Map<String, Object> data = resp == null ? null : resp.getData();
        if (data == null || data.isEmpty()) {
            return Map.of("error", "service_unavailable", "note", "听歌数据暂时取不到");
        }
        return data;
    }

    /** 关键字搜索 → 精简列表喂给模型，同时存入歌曲池供前端播放 */
    private Object searchSongs(String keyword, List<AiSongVO> sink) throws Exception {
        if (keyword.isBlank()) {
            return Map.of("songs", Collections.emptyList(), "note", "缺少关键字参数");
        }
        Result<Map<String, Object>> resp = songClient.search(keyword, 1, TOOL_SONG_LIMIT);
        List<Map<String, Object>> songs = extractSongs(resp);
        // 原始检索未命中：告知模型可换更短更独特的歌词片段重试
        if (songs.isEmpty()) {
            return Map.of("songs", songs,
                    "note", "曲库中没有匹配结果。若用户报的是歌词，请换一句更短更独特的歌词原文再试一次");
        }
        // 带上命中歌词的片段，模型可据此佐证"就是这句歌词"
        List<Map<String, Object>> view = simplify(songs, sink);
        for (int i = 0; i < view.size() && i < songs.size(); i++) {
            String snippet = lyricSnippetOf(songs.get(i));
            if (snippet != null) {
                view.get(i).put("lyricSnippet", snippet);
            }
        }
        return view;
    }

    /** 查歌手作品：先模糊匹配歌手名拿 ID，再逐个查其已发布歌曲 */
    private Object singerSongs(String singerName, List<AiSongVO> sink) throws Exception {
        if (singerName.isBlank()) {
            return Map.of("songs", Collections.emptyList(), "note", "缺少歌手名参数");
        }
        List<Long> ids = singerClient.searchIds(singerName);
        if (ids == null || ids.isEmpty()) {
            return Map.of("songs", Collections.emptyList(), "note", "曲库中没有叫「" + singerName + "」的歌手");
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Long id : ids.stream().limit(3).toList()) { // 防重名歌手爆量，最多取前3位
            try {
                Result<List<Map<String, Object>>> resp = songClient.bySinger(id);
                if (resp != null && resp.getData() != null) {
                    merged.addAll(resp.getData());
                }
            } catch (Exception e) {
                log.warn("查歌手作品失败: singerId={}, {}", id, e.getMessage());
            }
        }
        merged.sort((a, b) -> Long.compare(playCountOf(b), playCountOf(a)));
        return simplify(merged, sink);
    }

    private Object hotSongs(List<AiSongVO> sink) throws Exception {
        Result<List<Map<String, Object>>> resp = songClient.chart(TOOL_SONG_LIMIT);
        return simplify(resp == null || resp.getData() == null ? Collections.emptyList() : resp.getData(), sink);
    }

    /**
     * 把 song 服务返回的完整 Map 压成模型需要的最小字段集，同时存入歌曲池。
     * search/by-singer/chart 三接口均已含聚合好的 singerName，无需二次查 singer 服务。
     */
    private List<Map<String, Object>> simplify(List<Map<String, Object>> raw, List<AiSongVO> sink) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> entry : raw.stream().limit(TOOL_SONG_LIMIT).toList()) {
            AiSongVO vo = toAiSong(entry);
            if (vo == null) {
                continue;
            }
            sink.add(vo);
            // 喂给模型的精简视图（不含 url/lyric 等无关字段，控制 token）
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", vo.getId());
            item.put("name", vo.getName());
            item.put("singer", vo.getSingerName());
            item.put("style", vo.getStyle());
            item.put("playCount", vo.getPlayCount());
            out.add(item);
        }
        return out;
    }

    /** 把命中的歌词片段截取出来喂给模型，让模型能引用"对应歌词"佐证结果 */
    private static final int LYRIC_SNIPPET_MAX = 60;

    private String lyricSnippetOf(Map<String, Object> raw) {
        Object lyric = raw.get("lyric");
        if (lyric == null) {
            return null;
        }
        String text = String.valueOf(lyric).replaceAll("\\[[^\\]]*\\]", "").replace('\n', ' ').trim();
        return text.isEmpty() ? null : text.substring(0, Math.min(LYRIC_SNIPPET_MAX, text.length()));
    }

    /** song 服务返回的 Map → AI 歌曲项；字段异常整体丢弃（脏数据不进歌曲池） */
    private AiSongVO toAiSong(Map<String, Object> raw) {
        try {
            AiSongVO vo = new AiSongVO();
            vo.setId(Long.valueOf(String.valueOf(raw.get("id"))));
            Object name = raw.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                return null;
            }
            vo.setName(String.valueOf(name));
            vo.setSingerId(raw.get("singerId") == null ? null : Long.valueOf(String.valueOf(raw.get("singerId"))));
            vo.setSingerName((String) raw.get("singerName"));
            vo.setStyle((String) raw.get("style"));
            vo.setPlayCount(playCountOf(raw));
            return vo;
        } catch (Exception e) {
            log.warn("AI 歌曲项解析失败，跳过: {}", e.getMessage());
            return null;
        }
    }

    private long playCountOf(Map<String, Object> raw) {
        Object pc = raw.get("playCount");
        try {
            return pc == null ? 0L : Long.parseLong(String.valueOf(pc));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 从 song 服务的 SearchResultVO 结构里取出 songs 数组 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSongs(Result<Map<String, Object>> resp) {
        if (resp == null || resp.getData() == null) {
            return Collections.emptyList();
        }
        Object songs = resp.getData().get("songs");
        return songs instanceof List ? (List<Map<String, Object>>) songs : Collections.emptyList();
    }

    private void appendHistory(List<DeepseekChatRequest.Message> messages, List<AiChatRequest.HistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        int from = Math.max(0, history.size() - MAX_HISTORY);
        for (int i = from; i < history.size(); i++) {
            AiChatRequest.HistoryMessage h = history.get(i);
            if (h == null || h.getContent() == null || h.getContent().isBlank()
                    || (!"user".equals(h.getRole()) && !"assistant".equals(h.getRole()))) {
                continue;
            }
            messages.add(DeepseekChatRequest.Message.of(h.getRole(), h.getContent()));
        }
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String textOrFallback(String content) {
        return content == null || content.isBlank() ? "（AI 未返回内容，请重试）" : content;
    }

    private static AiChatVO done(String reply, List<AiSongVO> hitSongs, AiChatVO extra) {
        AiChatVO vo = new AiChatVO();
        vo.setReply(reply);
        vo.setSongs(dedup(hitSongs));
        vo.setCreatedPlaylistId(extra.getCreatedPlaylistId());
        vo.setCreatedPlaylistTitle(extra.getCreatedPlaylistTitle());
        return vo;
    }

    private static List<AiSongVO> dedup(List<AiSongVO> songs) {
        Map<Long, AiSongVO> byId = new LinkedHashMap<>();
        for (AiSongVO s : songs) {
            byId.putIfAbsent(s.getId(), s);
        }
        return new ArrayList<>(byId.values());
    }

    /** 工具定义：名称/描述/JSON Schema 参数 */
    private static List<DeepseekChatRequest.Tool> buildTools() {
        Map<String, Object> keywordSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "从用户话中提取的最有辨识度的检索词")),
                "required", List.of("keyword"));
        Map<String, Object> singerSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "singer_name", Map.of("type", "string", "description", "歌手名字")),
                "required", List.of("singer_name"));
        Map<String, Object> emptySchema = Map.of("type", "object", "properties", Map.of());
        Map<String, Object> createPlaylistSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string", "description", "歌单名称，贴合用户描述的场景/心情"),
                        "song_ids", Map.of("type", "array",
                                "items", Map.of("type", "integer"),
                                "description", "要收进歌单的歌曲ID列表，只能来自本轮工具返回的歌曲 id")),
                "required", List.of("title", "song_ids"));

        return List.of(
                DeepseekChatRequest.Tool.of("search_songs",
                        "按关键字搜索平台曲库。命中范围包括歌名、歌词、风格以及歌手名，支持歌词片段找歌。",
                        keywordSchema),
                DeepseekChatRequest.Tool.of("get_singer_songs",
                        "查询指定歌手的全部已发布歌曲。",
                        singerSchema),
                DeepseekChatRequest.Tool.of("get_hot_songs",
                        "获取平台当前最热门的歌曲榜单。当用户没有明确目标想随便听听时使用。",
                        emptySchema),
                DeepseekChatRequest.Tool.of("get_listen_report",
                        "查询当前用户的听歌周报（最近7天播放统计：播放次数、歌曲数、活跃天数、最爱歌曲TOP5、最爱歌手TOP3）。当用户询问自己的听歌情况、听歌报告、最近听了什么时使用。无需参数。",
                        emptySchema),
                DeepseekChatRequest.Tool.of("create_playlist",
                        "为当前用户创建歌单并批量加入歌曲。仅在用户明确希望建歌单、且已通过其他工具检索到歌曲后调用。",
                        createPlaylistSchema));
    }
}
