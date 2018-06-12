package com.yxhl.tcp.handler;

import io.netty.channel.socket.SocketChannel;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by zhangbin on 16/11/2.
 * 说明:这是一个容器类,帮助存储在线的会话
 */
public final class ChannelManager {
    //单例
    private static ChannelManager manager;
    //使用的并发容器,后期可选用其他方式升级
    private static Map<String, SocketChannel> userList = new ConcurrentHashMap();

    /**
     * constructer
     */
    private ChannelManager(){
    }

    /**
     * singeton
     * @return
     */
    public static ChannelManager getInstance(){
        if(manager  == null){
            synchronized (ChannelManager.class) {
                if(manager  == null){
                    manager = new ChannelManager();
                }
            }
        }
        return manager;
    }

    /**
     * add
     * @param key
     * @param channel
     */
    public void addChannel(String key,SocketChannel channel){
        userList.put(key,channel);
    }

    /**
     * get
     * @param key
     * @return
     */
    public SocketChannel getChannel(String key){
        return userList.get(key);
    }

    /**
     * del
     * @param key
     */
    public void delChannel(String key){
        userList.remove(key);
    }

    /**
     * 会话业务中用到所以添加这个方法
     * 作用:当线路断开的时候移除收录在线的通道
     * @param channel
     */
    public void removeChannel(SocketChannel channel){
        Iterator<Map.Entry<String, SocketChannel>> entries = userList.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, SocketChannel> entry = entries.next();
            if(entry.getValue().equals(channel)){
                entries.remove();
                return;
            }
        }
    }

}
