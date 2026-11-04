package com.rabbitc.ims.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** 在当前服务进程内隔离清空操作、正常 API 请求和事件投递。 */
@Component
public class DataMaintenanceGate implements HandlerInterceptor {
    private final ReentrantReadWriteLock gate = new ReentrantReadWriteLock(true);

    public Lock shared() {
        return gate.readLock();
    }

    public Lock exclusive() {
        return gate.writeLock();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        shared().lock();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception error) {
        shared().unlock();
    }
}
