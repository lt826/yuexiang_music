package com.qxt.yuexiang.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.message.entity.Message;
import com.qxt.yuexiang.message.feign.UserClient;
import com.qxt.yuexiang.message.mapper.MessageMapper;
import com.qxt.yuexiang.message.service.MessageService;
import com.qxt.yuexiang.message.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息服务实现：通知永不阻塞主流程（对齐概要设计降级策略）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserClient userClient;

    @Override
    public void send(Long userId, String title, String content, String type) {
        Message message = new Message();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setType(type == null ? "SYSTEM" : type);
        message.setIsRead(0);
        messageMapper.insert(message);
    }

    @Override
    public void broadcast(String title, String content) {
        List<Long> userIds;
        try {
            userIds = userClient.allIds();
        } catch (Exception e) {
            // 用户服务不可用时公告失败（admin 操作可重试），不影响其他业务
            log.error("群发公告拉取用户列表失败: {}", e.getMessage());
            throw new BusinessException("用户服务暂时不可用，请稍后重试");
        }
        for (Long userId : userIds) {
            try {
                send(userId, title, content, "SYSTEM");
            } catch (Exception e) {
                // 单个用户失败不中断群发
                log.warn("公告发送失败: userId={}, {}", userId, e.getMessage());
            }
        }
        log.info("系统公告已群发: 标题={}, 接收 {} 人", title, userIds.size());
    }

    @Override
    public Map<String, Object> myList(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .orderByDesc(Message::getCreateTime)
                        .last("LIMIT " + offset + ", " + size));
        List<MessageVO> vos = messages.stream().map(m -> {
            MessageVO vo = new MessageVO();
            BeanUtils.copyProperties(m, vo);
            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", vos);
        result.put("unread", unreadCount(userId));
        return result;
    }

    @Override
    public long unreadCount(Long userId) {
        return messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .eq(Message::getIsRead, 0));
    }

    @Override
    public void markRead(Long userId, Long messageId) {
        LambdaUpdateWrapper<Message> wrapper = new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1);
        if (messageId != null) {
            wrapper.eq(Message::getId, messageId);
        }
        messageMapper.update(null, wrapper);
    }

    // ===== 管理员收件箱（user_id=0，与普通用户消息物理隔离，杜绝串号） =====

    @Override
    public void notifyAdmins(String title, String content) {
        send(ADMIN_INBOX_USER_ID, title, content, "ADMIN");
        log.info("管理员待审通知已入收件箱: 标题={}", title);
    }

    @Override
    public List<Map<String, Object>> adminList(int page, int size) {
        int offset = (page - 1) * size;
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, ADMIN_INBOX_USER_ID)
                        .orderByDesc(Message::getCreateTime)
                        .last("LIMIT " + offset + ", " + size));
        return messages.stream().map(m -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", m.getId());
            row.put("title", m.getTitle());
            row.put("content", m.getContent());
            row.put("type", m.getType());
            row.put("isRead", m.getIsRead());
            row.put("createTime", m.getCreateTime());
            return row;
        }).collect(Collectors.toList());
    }

    @Override
    public long adminUnreadCount() {
        return messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, ADMIN_INBOX_USER_ID)
                        .eq(Message::getIsRead, 0));
    }

    @Override
    public void adminMarkRead(Long messageId) {
        LambdaUpdateWrapper<Message> wrapper = new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, ADMIN_INBOX_USER_ID)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1);
        if (messageId != null) {
            wrapper.eq(Message::getId, messageId);
        }
        messageMapper.update(null, wrapper);
    }
}
