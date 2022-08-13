package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone 手机号
     * @return result
     */
    Result sendCode(String phone);

    /**
     * 登录
     * @param loginForm loginForm
     * @param session session
     * @return result
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result test();
}
