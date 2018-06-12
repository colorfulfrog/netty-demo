/*
* Copyright (c) 2014 Javaranger.com. All Rights Reserved.
*/
package com.yxhl.tcp.cache;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * @Desc Redis缓存访问
 *
 */

@Component
public class RedisDao {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static JedisPool jedisPool;

    static {
        if(jedisPool ==null){
            jedisPool = new JedisPool(new GenericObjectPoolConfig(),"101.200.241.34", 6379,2000,"YueXingHuLian@45435361094");
        }
    }



    /**
     * gou zao
     */
    public RedisDao() {

    }

    /**
     * simple del
     * @param msg
     */
    public void delMsg(String msg){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = msg;
                jedis.del(msg);

            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * simple del
     * @param msg
     */
    public boolean hasKey(String msg){
        boolean flag=false;
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = msg;
                String value=jedis.get(msg);
                if(value != null && value != ""){
                    flag=true;
                }
            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            flag= false;
        }
        return flag;
    }

    /**
     * string get
     * @param msg
     * @return
     */
    public String getMsg(String msg){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = msg;
                String value=jedis.get(msg);

                if (null != value) {
                    //空对象

                    return value;
                }
            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * @Desc 获取缓存对象
     */
    public void putMsg(String key,String value ){
        //redis操作逻辑
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                jedis.set(key,value);
            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        RedisDao dao =new RedisDao();

        String key="aa";
        String value="bb";
        dao.putMsg(key,value);
        System.out.println(dao.getMsg(key));
    }

}
