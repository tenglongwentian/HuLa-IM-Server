package com.hula.core.user.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.StrUtil;
import com.hula.common.constant.RedisKey;
import com.hula.common.enums.LoginTypeEnum;
import com.hula.common.event.TokenExpireEvent;
import com.hula.common.event.UserOfflineEvent;
import com.hula.core.user.domain.entity.User;
import com.hula.core.user.service.TokenService;
import com.hula.utils.JwtUtils;
import com.hula.utils.RedisUtils;
import com.hula.utils.RequestHolder;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hula.common.config.ThreadPoolConfig.HULA_EXECUTOR;

/**
 * @author nyh
 */
@Service
@AllArgsConstructor
public class TokenServiceImpl implements TokenService {

    // token过期时间
    private static final Integer TOKEN_EXPIRE_DAYS = 5;
    // token续期时间
    private static final Integer TOKEN_RENEWAL_DAYS = 2;

    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * 校验token是不是有效
     */
    @Override
    public boolean verify(String token) {
        Long uid = JwtUtils.getUidOrNull(token);
        if (Objects.isNull(uid)) {
            return false;
        }
        // 有可能token失效了，需要校验是不是和最新token一致
        return Objects.equals(token, RedisUtils.getStr(RedisKey.getKey(RedisKey.USER_TOKEN_FORMAT,
                JwtUtils.getLoginType(token), uid)));
    }

    @Async(HULA_EXECUTOR)
    @Override
    public void renewalTokenIfNecessary(String token) {
        Long uid = JwtUtils.getUidOrNull(token);
        if (Objects.isNull(uid)) {
            return;
        }
        String key = RedisKey.getKey(RedisKey.USER_TOKEN_FORMAT, JwtUtils.getLoginType(token), uid);
        long expireDays = RedisUtils.getExpire(key, TimeUnit.DAYS);
        // 不存在的key
        if (expireDays == -2) {
            return;
        }
        // 小于一天的token帮忙续期
        if (expireDays < TOKEN_RENEWAL_DAYS) {
            RedisUtils.expire(key, TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        }
    }

    @Override
    public String createToken(Long uid, LoginTypeEnum loginTypeEnum) {
        String key = RedisKey.getKey(RedisKey.USER_TOKEN_FORMAT, loginTypeEnum.getType(), uid);
        String token = RedisUtils.getStr(key);
        if (StrUtil.isNotBlank(token)) {
            // 旧token删除
            RedisUtils.del(key);
            User user = User.builder().id(uid).build();
            user.refreshIp(RequestHolder.get().getIp());
            // 旧设备下线
            applicationEventPublisher.publishEvent(new TokenExpireEvent(this, user));
        }
        // 获取用户token
        token = JwtUtils.createToken(uid, loginTypeEnum.getType());
        // token过期用redis中心化控制，初期采用5天过期，剩1天自动续期的方案。后续可以用双token实现
        RedisUtils.set(key, token, TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        return token;
    }

    @Override
    public void refreshToken(User user) {
        RedisUtils.expire(RedisKey.getKey(RedisKey.USER_TOKEN_FORMAT,
                        JwtUtils.getLoginType(RequestHolder.get().getToken()),
                        RequestHolder.get().getUid()),
                TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    @Override
    public void offline(User user) {
        // 下线
        applicationEventPublisher.publishEvent(new UserOfflineEvent(this,
                User.builder().id(RequestHolder.get().getUid()).lastOptTime(DateTime.now()).build()));
    }

}
