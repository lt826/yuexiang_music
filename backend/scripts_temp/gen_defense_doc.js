const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, LevelFormat, HeadingLevel, BorderStyle,
  WidthType, ShadingType, PageNumber, PageBreak,
} = require("docx");

// ============ 通用构件 ============
const FONT = "Microsoft YaHei";
const border = { style: BorderStyle.SINGLE, size: 1, color: "BBBBBB" };
const borders = { top: border, bottom: border, left: border, right: border };

function h1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(text)] });
}
function h2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(text)] });
}
function h3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(text)] });
}
function p(text, opts = {}) {
  return new Paragraph({
    spacing: { after: 120 },
    children: [new TextRun({ text, size: 22, font: FONT, ...opts })],
  });
}
function pRuns(runs) {
  return new Paragraph({ spacing: { after: 120 }, children: runs.map(r => new TextRun({ size: 22, font: FONT, ...r })) });
}
function bullet(text) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    spacing: { after: 60 },
    children: [new TextRun({ text, size: 22, font: FONT })],
  });
}
function bulletRuns(runs) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    spacing: { after: 60 },
    children: runs.map(r => new TextRun({ size: 22, font: FONT, ...r })),
  });
}
function code(lines) {
  return lines.map(l => new Paragraph({
    spacing: { after: 0, line: 240 },
    shading: { fill: "F5F5F5", type: ShadingType.CLEAR },
    children: [new TextRun({ text: l.length ? l : " ", size: 18, font: "Consolas" })],
  }));
}
function spacer() { return new Paragraph({ spacing: { after: 120 }, children: [] }); }

function table(headers, rows, widths) {
  const total = 9360;
  const colW = widths || headers.map(() => Math.floor(total / headers.length));
  return new Table({
    width: { size: total, type: WidthType.DXA },
    columnWidths: colW,
    rows: [
      new TableRow({
        tableHeader: true,
        children: headers.map((htext, i) => new TableCell({
          borders,
          width: { size: colW[i], type: WidthType.DXA },
          shading: { fill: "DEEAF6", type: ShadingType.CLEAR },
          margins: { top: 80, bottom: 80, left: 120, right: 120 },
          children: [new Paragraph({ children: [new TextRun({ text: htext, size: 20, bold: true, font: FONT })] })],
        })),
      }),
      ...rows.map(r => new TableRow({
        children: r.map((cell, i) => new TableCell({
          borders,
          width: { size: colW[i], type: WidthType.DXA },
          margins: { top: 80, bottom: 80, left: 120, right: 120 },
          children: [new Paragraph({ children: [new TextRun({ text: cell, size: 20, font: FONT })] })],
        })),
      })),
    ],
  });
}

// ============ 文档内容 ============
const children = [];

// 标题
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { after: 80 },
  children: [new TextRun({ text: "消息队列与AI问答功能答辩文档", size: 44, bold: true, font: FONT })],
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { after: 300 },
  children: [new TextRun({ text: "项目：悦享音乐（Music Dreamer）微服务平台　　答辩人：刘涛", size: 22, font: FONT, color: "666666" })],
}));

// ================= 1. 流程概述 =================
children.push(h1("1. 流程概述（必填）"));

children.push(h2("1.1 消息队列（RabbitMQ）"));
children.push(p("解决什么问题：用户播放歌曲后要同时完成「写播放计数、记收听历史」，歌手上架歌曲后要给粉丝发通知——这些操作如果同步做会拖慢用户请求，且服务之间耦合严重。引入 RabbitMQ 后，song 服务只负责发一条消息，其余工作由各服务异步消费完成。"));
children.push(p("输入：播放事件、歌曲上架事件（JSON 消息）。"));
children.push(p("输出：Redis 热榜计数更新、收听历史落库、粉丝收到站内通知。"));

children.push(h2("1.2 AI 问答（DeepSeek 大模型）"));
children.push(p("解决什么问题：让用户用一句话（如「想听伤感的歌」「帮我建个跑步歌单」）完成找歌、看听歌周报、建歌单等操作。"));
children.push(p("输入：用户提问 + 对话历史。"));
children.push(p("输出：AI 回答文本 + 命中的歌曲列表（前端可直接播放）+ 可能新建的歌单。"));

// ================= 2. 消息队列原理与实现 =================
children.push(new Paragraph({ children: [new PageBreak()] }));
children.push(h1("2. 消息队列功能：原理、调用位置与实现"));

children.push(h2("2.1 原理"));
children.push(p("RabbitMQ 是一个消息中转站。发送方（song 服务）把消息投递到「交换机」，交换机按路由键把消息分发到一个或多个「队列」，消费者（song/favorite/message 服务）各自监听自己的队列取消息处理。这样发送方和接收方互不等待、互不依赖，任何一方临时故障不影响另一方。"));

children.push(h2("2.2 在哪调用"));
children.push(table(
  ["环节", "所在类", "做什么"],
  [
    ["发布播放事件", "song 服务 PlayEventPublisher.publishPlay()", "用户点播放时，把 songId/userId 打包成 JSON 发到 yuexiang.play.exchange"],
    ["发布上架事件", "song 服务 PlayEventPublisher.publishSongPublished()", "歌曲审核通过时，发 songId/singerId/songName 到 yuexiang.song.published.exchange"],
    ["消费播放事件（计数）", "song 服务 PlayEventPublisher.onPlay()（@RabbitListener）", "写 Redis 计数缓冲，ZINCRBY 更新实时热榜"],
    ["消费播放事件（历史）", "favorite 服务 PlayEventConsumer.onPlay()", "向 listen_history 表插入一条收听记录"],
    ["消费上架事件（粉丝）", "favorite 服务 SongPublishedConsumer", "查出收藏过该歌手歌曲的粉丝名单，转发给 message 服务"],
    ["消费上架事件（通知）", "message 服务 SongPublishedConsumer", "按粉丝名单逐个发送站内通知"],
  ]
));

children.push(h2("2.3 核心实现"));
children.push(p("发送：注入 Spring 的 RabbitTemplate，一个方法完成发送；MQ 不可用时降级为直接写 Redis，播放不受影响："));
children.push(...code([
  "// song 服务 PlayEventPublisher.java",
  "rabbitTemplate.convertAndSend(",
  "        MqConstants.PLAY_EXCHANGE,          // 交换机",
  "        MqConstants.PLAY_ROUTING_KEY,       // 路由键 play.report",
  "        JSON.writeValueAsString(event));    // 消息体 JSON",
]));
children.push(p("消费：@RabbitListener 监听队列，处理成功才手动 basicAck 确认；消费端宕机时未确认的消息自动重投，保证不丢："));
children.push(...code([
  "@RabbitListener(queues = MqConstants.PLAY_QUEUE_SONG,",
  "        containerFactory = \"manualAckContainerFactory\")",
  "public void onPlay(Message raw, Channel channel) {",
  "    // ... 解析消息、写 Redis ...",
  "    channel.basicAck(deliveryTag, false);   // 处理成功才确认",
  "}",
]));

// ================= 3. AI 问答原理与实现 =================
children.push(new Paragraph({ children: [new PageBreak()] }));
children.push(h1("3. AI 问答功能：原理、调用位置与实现"));

children.push(h2("3.1 第一步：配置 API Key"));
children.push(p("在 ai 服务的 application.yml 中配置 DeepSeek 的接口地址、API Key 和模型名（Key 从环境变量注入，不写死在代码里）："));
children.push(...code([
  "# yuexiang-ai/src/main/resources/application.yml",
  "deepseek:",
  "  base-url: https://api.deepseek.com",
  "  api-key: ${DEEPSEEK_API_KEY}",
  "  model: deepseek-chat",
  "  timeout-seconds: 60",
]));

children.push(h2("3.2 第二步：从哪个类调用大模型"));
children.push(table(
  ["类", "职责"],
  [
    ["DeepseekClient", "封装 HTTP 调用的客户端类。构造时读取 yml 里的 base-url 和 api-key，用 Spring WebClient 带 Bearer 鉴权头，POST 请求 /chat/completions 接口，返回 DeepseekChatResponse"],
    ["AiSearchService", "业务编排类。chat() 方法组装「系统提示词 + 对话历史 + 用户提问 + 工具清单」发给 DeepseekClient，处理模型的返回"],
    ["AiController", "HTTP 入口。POST /ai/chat 接收前端提问，调用 AiSearchService.chat()"],
  ]
));

children.push(h2("3.3 第三步：工具调用（Function Calling）"));
children.push(p("大模型本身连不了数据库，不知道平台里有什么歌。解决办法是 Function Calling：把平台的能力包装成 5 个「工具」，以 JSON Schema 形式随请求发给模型，模型按用户意图决定调哪个工具、传什么参数："));
children.push(table(
  ["工具名", "功能", "实际执行"],
  [
    ["search_songs", "按关键词/歌词片段找歌", "Feign 调 song 服务搜索接口"],
    ["get_singer_songs", "查某歌手的全部歌曲", "Feign 调 song 服务"],
    ["get_hot_songs", "获取实时热榜", "Feign 调 song 服务"],
    ["get_listen_report", "查当前用户听歌周报", "Feign 调 favorite 服务"],
    ["create_playlist", "创建歌单并加入歌曲", "Feign 调 playlist 服务"],
  ]
));
children.push(p("完整流程：① AiSearchService 把提问和工具清单发给 DeepSeekClient → ② 模型返回 tool_calls（要调的工具名+参数）→ ③ 服务用 Feign 执行真实查询，结果以 role=tool 消息回填 → ④ 再次请求模型，模型判断信息够了就返回 finish_reason=stop 的最终回答。循环最多 8 轮。全程歌曲数据都是实时查库的，模型只负责理解意图，不编造内容。"));
children.push(...code([
  "// AiSearchService.java 核心循环（节选）",
  "for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {   // 最多 8 轮",
  "    DeepseekChatResponse response = deepseekClient.chat(buildRequest(messages, true));",
  "    var toolCalls = choice.getMessage().getToolCalls();",
  "    if (toolCalls == null || toolCalls.isEmpty()) break;  // 模型给出最终回答",
  "    for (var toolCall : toolCalls) {",
  "        String resultJson = executeTool(toolCall.getFunction().getName(),",
  "                toolCall.getFunction().getArguments(), hitSongs, request.getUserId(), extra);",
  "        messages.add(DeepseekChatRequest.Message.toolResult(toolCall.getId(), resultJson));",
  "    }",
  "}",
]));

// ================= 4. 总结 =================
children.push(new Paragraph({ children: [new PageBreak()] }));
children.push(h1("4. 总结"));
children.push(p("消息队列：song 服务在用户播放、歌曲上架两个时机用 RabbitTemplate 发事件，song/favorite/message 三个服务各自 @RabbitListener 监听队列异步消费（热榜计数、收听历史、粉丝通知），手动 ACK 保证消息不丢，实现了服务间解耦和流量削峰。"));
children.push(p("AI 问答：ai 服务在 application.yml 配置 DeepSeek 的 API Key，DeepseekClient 类负责 HTTP 调用大模型，AiSearchService 通过 Function Calling 让模型在 5 个平台工具（搜索/歌手/热榜/周报/建歌单）中自主选择调用，工具结果实时查库回填，最多 8 轮后输出回答——大模型是平台的自然语言入口，所有数据真实可信。"));


// ============ 组装文档 ============
const doc = new Document({
  styles: {
    default: { document: { run: { font: FONT, size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: FONT },
        paragraph: { spacing: { before: 280, after: 200 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: FONT },
        paragraph: { spacing: { before: 220, after: 160 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 23, bold: true, font: FONT },
        paragraph: { spacing: { before: 160, after: 120 }, outlineLevel: 2 } },
    ],
  },
  numbering: {
    config: [
      { reference: "bullets",
        levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },   // A4
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      },
    },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.RIGHT,
        border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "9CB6D8", space: 1 } },
        children: [new TextRun({ text: "悦享音乐 · 消息队列与AI问答功能答辩文档", size: 16, color: "888888", font: FONT })] })] }),
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          new TextRun({ text: "第 ", size: 16, font: FONT }),
          new TextRun({ children: [PageNumber.CURRENT], size: 16, font: FONT }),
          new TextRun({ text: " 页", size: 16, font: FONT }),
        ] })] }),
    },
    children,
  }],
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync("D:/Desktop/悦享音乐/消息队列与AI问答功能答辩文档.docx", buffer);
  console.log("DOCX written OK");
});
